package com.gjl.music.service.watch;

import com.gjl.music.mapper.MusicMapper;
import com.gjl.music.model.MusicMetadata;
import com.gjl.music.model.WatchRule;
import com.gjl.music.parser.ParserFactory;
import com.gjl.music.persistence.MetadataPersister;
import com.gjl.music.watch.FileWatchHandler;
import com.gjl.music.watch.WatchRuleMatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@Component
public class WatchRuleFileWatchHandler implements FileWatchHandler {

    private final WatchRuleService ruleService;
    private final ParserFactory parserFactory;
    private final MetadataPersister persister;
    private final MusicMapper musicMapper;
    private final Path rootDir;

    public WatchRuleFileWatchHandler(
            WatchRuleService ruleService,
            ParserFactory parserFactory,
            MetadataPersister persister,
            MusicMapper musicMapper,
            @Value("${music.root-dir}") String rootDir) {
        this.ruleService = ruleService;
        this.parserFactory = parserFactory;
        this.persister = persister;
        this.musicMapper = musicMapper;
        this.rootDir = Path.of(rootDir).toAbsolutePath().normalize();
    }

    @Override
    public void onNewFiles(List<Path> batch) {
        processFiles(batch);
    }

    @Override
    public void onModifiedFiles(List<Path> batch) {
                processFiles(batch);
    }

    @Override
    public void onDeletedFiles(List<Long> songIds, List<Path> paths) {
        if (songIds.isEmpty()) return;
        try {
            List<Long> ids = new ArrayList<>(songIds);
            musicMapper.deleteSongArtistsBySongIds(ids);
            musicMapper.deleteSongStylesBySongIds(ids);
            musicMapper.deleteLyricsBySongIds(ids);
            musicMapper.deleteSongsByIds(ids);
            log.info("已清理 {} 个已删除文件的 DB 记录", ids.size());
        } catch (Exception e) {
            log.error("清理删除文件 DB 记录失败", e);
        }
    }


    private void processFiles(List<Path> batch) {
        if (batch.isEmpty()) return;

        Map<Long, List<Path>> profileBatches = new LinkedHashMap<>();
        List<Path> unmatched = new ArrayList<>();

        for (Path file : batch) {
            String relPath = rootDir.relativize(file).toString();
            WatchRule profile = WatchRuleMatcher.findMatching(ruleService.getAutoRules(), relPath);
            if (profile != null) {
                profileBatches.computeIfAbsent(profile.getId(), k -> new ArrayList<>()).add(file);
            } else {
                unmatched.add(file);
            }
        }

                for (var entry : profileBatches.entrySet()) {
            try {
                String pipelineId = ruleService.submitBatch(entry.getKey(), entry.getValue());
                if (pipelineId != null) {
                    log.info("已提交管道 {}: profileId={}, {} 个文件",
                            pipelineId, entry.getKey(), entry.getValue().size());
                }
            } catch (Exception e) {
                log.error("提交管道失败: profileId={}", entry.getKey(), e);
            }
        }

                if (!unmatched.isEmpty()) {
            defaultPersist(unmatched);
        }
    }


    private void defaultPersist(List<Path> files) {
        List<Map.Entry<String, MusicMetadata>> batch = new ArrayList<>();
        for (Path file : files) {
            try {
                var meta = parserFactory.parse(file.toFile());
                if (meta != null && !meta.getImmutableSongs().isEmpty()) {
                    batch.add(Map.entry(file.toAbsolutePath().toString(), meta));
                }
            } catch (Exception e) {
                log.error("默认入库失败: {} —— {}", file.getFileName(), e.getMessage());
            }
        }
        if (!batch.isEmpty()) {
            try {
                persister.persistBatch(batch);
                log.info("默认批量入库: {} 个文件", batch.size());
            } catch (Exception e) {
                log.error("默认批量入库失败", e);
            }
        }
    }
}
