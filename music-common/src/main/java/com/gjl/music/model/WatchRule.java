package com.gjl.music.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;


public class WatchRule {

    private Long id;
    private String name;
    private String watchPath;
    private boolean enabled = true;
    private boolean autoTrigger = true;
    private String stepsJson;
    private int priority;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;


    private List<WatchStep> steps = Collections.emptyList();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getWatchPath() { return watchPath; }
    public void setWatchPath(String watchPath) { this.watchPath = watchPath; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isAutoTrigger() { return autoTrigger; }
    public void setAutoTrigger(boolean autoTrigger) { this.autoTrigger = autoTrigger; }

    public String getStepsJson() { return stepsJson; }
    public void setStepsJson(String stepsJson) { this.stepsJson = stepsJson; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public List<WatchStep> getSteps() { return steps; }
    public void setSteps(List<WatchStep> steps) { this.steps = steps != null ? steps : Collections.emptyList(); }

    @Override
    public String toString() {
        return "WatchRule{id=" + id + ", name=" + name + ", watchPath=" + watchPath
                + ", enabled=" + enabled + ", steps=" + steps.size() + '}';
    }
}
