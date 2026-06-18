package com.gjl.music.playback.service.impl;
import com.gjl.music.playback.infra.search.IndexConfig;
import com.gjl.music.playback.infra.search.IndexManager;
import com.gjl.music.playback.service.SearchService;

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
        if (q.isEmpty()) return List.of();

                if (isUUID(q)) {
                        return List.of();
        }

                if (q.length() < 1) return List.of();

        try {
                        MultiFieldQueryParser parser = new MultiFieldQueryParser(
                    IndexConfig.SEARCH_FIELDS, analyzer, IndexConfig.FIELD_BOOST);
            parser.setDefaultOperator(QueryParser.Operator.OR);
            Query luceneQuery = parser.parse(q);

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
                String id = doc.get(IndexConfig.FIELD_ID);
                if (id != null) {
                    try {
                        results.add(Long.valueOf(id));
                    } catch (NumberFormatException ignored) {}
                }
            }

            return results;
        } catch (org.apache.lucene.queryparser.classic.ParseException e) {
            log.debug("Lucene 查询语法错误: query={}, error={}", q, e.getMessage());
            return List.of();
        } catch (IOException e) {
            log.warn("Lucene 搜索异常: {}", e.getMessage());
            return List.of();
        }
    }


    private static String preprocess(String query) {
        if (query == null) return "";
        String q = query.trim();
                while (q.endsWith("*")) q = q.substring(0, q.length() - 1).trim();
        return q;
    }

    private static boolean isUUID(String s) {
        return s.length() == 36 && s.indexOf('-') == 8;
    }
}
