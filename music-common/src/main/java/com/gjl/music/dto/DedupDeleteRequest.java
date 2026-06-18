package com.gjl.music.dto;

import java.util.Map;


public class DedupDeleteRequest {

    private String action;
    private String filePath;
    private String strategy;
    private String rule;
    private Map<String, Object> ruleOptions;

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }

    public String getRule() { return rule; }
    public void setRule(String rule) { this.rule = rule; }

    public Map<String, Object> getRuleOptions() { return ruleOptions; }
    public void setRuleOptions(Map<String, Object> ruleOptions) { this.ruleOptions = ruleOptions; }
}
