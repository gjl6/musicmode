package com.gjl.music.module.song.writer;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Writer 模块的产出：记录写回操作的成功/失败文件列表 */
@Getter
@Setter
public class WriteResult {

    private int successCount;
    private int failCount;
    private int totalCount;
    private final List<String> successFiles = new CopyOnWriteArrayList<>();
    private final List<String> failureFiles = new CopyOnWriteArrayList<>();

    public void addSuccess(String filePath) {
        successCount++;
        totalCount++;
        successFiles.add(filePath);
    }

    public void addFailure(String filePath) {
        failCount++;
        totalCount++;
        failureFiles.add(filePath);
    }

    @Override
    public String toString() {
        return "WriteResult{success=" + successCount
                + ", failure=" + failCount
                + ", total=" + totalCount + "}";
    }
}
