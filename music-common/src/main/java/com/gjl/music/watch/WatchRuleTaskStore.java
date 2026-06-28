package com.gjl.music.watch;

import com.gjl.music.mapper.WatchRuleMapper;
import com.gjl.music.model.WatchRule;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link TaskStore} 的 editor 实现 — 委托 {@link WatchRuleMapper} 进行 DB 持久化。
 */
@Component
public class WatchRuleTaskStore implements TaskStore {

    private final WatchRuleMapper mapper;

    public WatchRuleTaskStore(WatchRuleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<WatchRule> loadAll() {
        return mapper.selectAll();
    }

    @Override
    public WatchRule load(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public void updateState(Long id, String state, LocalDateTime scanAt,
                            LocalDateTime runAt, String error) {
        mapper.updateState(id, state, scanAt, runAt, error);
    }
}
