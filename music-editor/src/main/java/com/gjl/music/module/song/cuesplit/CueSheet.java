package com.gjl.music.module.song.cuesplit;

import java.util.*;

/**
 * CUE 文件解析后的数据模型。
 *
 * <p>CUE 文件结构示例：
 * <pre>{@code
 * PERFORMER "Album Artist"
 * TITLE "Album Title"
 * FILE "audio.flac" WAVE
 *   TRACK 01 AUDIO
 *     TITLE "Track Title"
 *     PERFORMER "Track Artist"
 *     INDEX 00 00:00:00    (可选: pregap)
 *     INDEX 01 00:00:33    (实际起始时间, MM:SS:FF, 75帧/秒)
 * }</pre>
 */
public class CueSheet {

    private String performer;       // 专辑级 PERFORMER
    private String title;           // 专辑级 TITLE
    private final List<CueFile> files = new ArrayList<>();

    public String getPerformer() { return performer; }
    public void setPerformer(String performer) { this.performer = performer; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public List<CueFile> getFiles() { return Collections.unmodifiableList(files); }
    public void addFile(CueFile file) { this.files.add(file); }

    /** CUE FILE 条目 */
    public static class CueFile {
        private String fileName;    // FILE "audio.flac" WAVE → audio.flac
        private String fileType;    // WAVE, MP3, FLAC, AIFF
        private final List<CueTrack> tracks = new ArrayList<>();

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }

        public List<CueTrack> getTracks() { return Collections.unmodifiableList(tracks); }
        public void addTrack(CueTrack track) { this.tracks.add(track); }
    }

    /** CUE TRACK 条目 */
    public static class CueTrack {
        private int trackNumber;    // 01, 02, ...
        private String type;        // AUDIO (仅支持类型)
        private String title;       // TRACK 级 TITLE
        private String performer;   // TRACK 级 PERFORMER
        private int index00Ms = -1; // INDEX 00 毫秒 (pregap), -1 表示不存在
        private int index01Ms;      // INDEX 01 毫秒 (实际起始时间)

        public int getTrackNumber() { return trackNumber; }
        public void setTrackNumber(int trackNumber) { this.trackNumber = trackNumber; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getPerformer() { return performer; }
        public void setPerformer(String performer) { this.performer = performer; }

        public int getIndex00Ms() { return index00Ms; }
        public void setIndex00Ms(int index00Ms) { this.index00Ms = index00Ms; }

        public int getIndex01Ms() { return index01Ms; }
        public void setIndex01Ms(int index01Ms) { this.index01Ms = index01Ms; }

        /** 获取 INDEX 01 的时长字符串 (mm:ss) */
        public String getStartTimeDisplay() {
            int totalSec = index01Ms / 1000;
            return String.format("%02d:%02d", totalSec / 60, totalSec % 60);
        }
    }
}
