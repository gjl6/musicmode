package com.gjl.music.dto;

import java.util.Map;

/**
 * 去重删除请求体 —— 统一处理逐文件删除和规则批量删除。
 *
 * <p>action 取值：
 * <ul>
 *   <li>{@code single} — 删除单个文件，需提供 filePath</li>
 *   <li>{@code rule}   — 按规则批量删除，需提供 strategy + rule + ruleOptions</li>
 * </ul>
 *
 * <p>rule 取值：
 * <ul>
 *   <li>{@code keepFirst} — 保留每组第一个文件</li>
 *   <li>{@code keepByDirectory} — 保留指定目录下的文件</li>
 *   <li>{@code keepPreferredFormat} — 保留首选格式的文件</li>
 * </ul>
 */
public class DedupDeleteRequest {

    private String action;                     // single | rule
    private String filePath;                   // action=single 时使用
    private String strategy;                   // action=rule 时使用
    private String rule;                       // keepFirst | keepByDirectory | keepPreferredFormat
    private Map<String, Object> ruleOptions;   // { directory, preferredFormats }

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
