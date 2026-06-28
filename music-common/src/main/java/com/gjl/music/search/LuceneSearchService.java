package com.gjl.music.search;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lucene 全文搜索服务实现。
 *
 * <p>三路并行搜索（歌曲/专辑/艺术家），SmartChineseAnalyzer 分词，BM25 排序。
 */
@Slf4j
@Service
public class LuceneSearchService implements SearchService {

    private final IndexManager indexManager;
    private final Analyzer analyzer;

    public LuceneSearchService(IndexManager indexManager) {
        this.indexManager = indexManager;
        this.analyzer = IndexConfig.createAnalyzer();
    }

    @Override
    public List<Long> searchSongs(String query, int offset, int limit) {
        return search(query, offset, limit, "song");
    }

    @Override
    public List<Long> searchAlbums(String query, int offset, int limit) {
        return search(query, offset, limit, "album");
    }

    @Override
    public List<Long> searchArtists(String query, int offset, int limit) {
        return search(query, offset, limit, "artist");
    }

    private List<Long> search(String query, int offset, int limit, String type) {
        IndexSearcher searcher = indexManager.getSearcher();
        if (searcher == null) return List.of();

        String q = preprocess(query);

        // UUID 检测：跳过 Lucene，直接查 ID
        if (isUUID(q)) {
            return List.of();
        }

        log.info("[LuceneSearch] type={}, query='{}', offset={}, limit={}, searcherTotalDocs={}",
                type, q, offset, limit, searcher.getIndexReader().numDocs());
        try {
            Query luceneQuery;
            if (q.isEmpty()) {
                // 空查询 → 匹配全部（Subsonic 客户端如 Musiver 发空查询获取全部歌曲）
                luceneQuery = new MatchAllDocsQuery();
                log.info("[LuceneSearch] 使用 MatchAllDocsQuery, type={}", type);
            } else {
                // 按类型选择搜索字段和权重
                String[] searchFields;
                Map<String, Float> boosts;
                switch (type) {
                    case "album":
                        searchFields = IndexConfig.SEARCH_FIELDS_ALBUM;
                        boosts = IndexConfig.ALBUM_BOOST;
                        break;
                    case "artist":
                        searchFields = IndexConfig.SEARCH_FIELDS_ARTIST;
                        boosts = IndexConfig.ARTIST_BOOST;
                        break;
                    default:
                        searchFields = IndexConfig.SEARCH_FIELDS_SONG;
                        boosts = IndexConfig.SONG_BOOST;
                }
                MultiFieldQueryParser parser = new MultiFieldQueryParser(
                        searchFields, analyzer, boosts);
                parser.setDefaultOperator(QueryParser.Operator.OR);
                luceneQuery = parser.parse(q);
            }

            // 类型过滤
            Query typeFilter = new TermQuery(new Term(IndexConfig.FIELD_TYPE, type));
            BooleanQuery boolQuery = new BooleanQuery.Builder()
                    .add(luceneQuery, BooleanClause.Occur.MUST)
                    .add(typeFilter, BooleanClause.Occur.FILTER)
                    .build();

            int topN = offset + limit;
            TopDocs topDocs = searcher.search(boolQuery, Math.max(topN, 100));

            List<Long> results = new ArrayList<>();
            for (int i = offset; i < Math.min(topDocs.scoreDocs.length, topN); i++) {
                var doc = searcher.storedFields()
                        .document(topDocs.scoreDocs[i].doc);
                Long id = IndexConfig.parseStoredId(doc.get(IndexConfig.FIELD_ID));
                if (id != null) {
                    results.add(id);
                }
            }

            log.info("[LuceneSearch] type={}, totalHits={}, returned={}", type, topDocs.totalHits.value, results.size());
            return results;
        } catch (org.apache.lucene.queryparser.classic.ParseException e) {
            log.debug("Lucene 查询语法错误: query={}, error={}", q, e.getMessage());
            return List.of();
        } catch (IOException e) {
            log.warn("Lucene 搜索异常: {}", e.getMessage());
            return List.of();
        }
    }

    // ── private helpers ──

    private static String preprocess(String query) {
        if (query == null) return "";
        String q = query.trim();
        // 去掉首尾引号（Subsonic 客户端如 Musiver 发 "" 表示获取全部）
        while ((q.startsWith("\"") && q.endsWith("\"")) || (q.startsWith("'") && q.endsWith("'"))) {
            q = q.substring(1, q.length() - 1).trim();
        }
        // 去掉尾部 *
        while (q.endsWith("*")) q = q.substring(0, q.length() - 1).trim();
        return q;
    }

    private static boolean isUUID(String s) {
        return s.length() == 36 && s.indexOf('-') == 8;
    }
}
