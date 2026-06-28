package com.gjl.music.service.song;

import java.util.Map;

/**
 * 工具执行服务接口 —— 每个工具方法提交一个异步管道，返回 pipelineId。
 */
public interface ToolService {

    String repairEncoding(Map<String, Object> options);
    String convertChinese(Map<String, Object> options);
    String enrichMetadata(Map<String, Object> options);
    String detectDuplicates(Map<String, Object> options);
    String writeTags(Map<String, Object> options);
    String importToDatabase(Map<String, Object> options);
    String executeSplit(Map<String, Object> options);
    String executeReplace(Map<String, Object> options);
    String formatConvert(Map<String, Object> options);
    String cueSplit(Map<String, Object> options);
    String organizeFiles(Map<String, Object> options);
    String deleteFiles(Map<String, Object> options);
}
