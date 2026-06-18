package com.gjl.music.parser;

import com.gjl.music.exception.InvalidFileException;
import com.gjl.music.exception.MetadataParseException;
import com.gjl.music.infra.util.AudioFileUtils;
import com.gjl.music.infra.util.FileHashUtils;
import com.gjl.music.model.*;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.id3.AbstractID3v2Frame;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
import org.jaudiotagger.tag.images.Artwork;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Slf4j
public class DefaultParser {


    private String artistSplitConfig = """
["\\\\", ",", ";", "&", "+", "|", "、", "，", "/", "_", "ft.", "feat.", "featuring", "presents", "pres.", "vs.", "versus", "x", " ", "\\\\u0000"]
""";

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();


    public void setArtistSplitConfig(String config) {
        if (config != null && !config.isBlank()) {
            this.artistSplitConfig = config;
        }
    }

    public MusicMetadata parse(File file) throws MetadataParseException {
        validateFile(file);
        try {
            AudioFile af = AudioFileIO.read(file);
            Tag tag = af.getTagOrCreateDefault();
            AudioHeader header = af.getAudioHeader();
            String fileName = file.getName();
            String filePath = file.getAbsolutePath();

            MusicMetadata meta = new MusicMetadata();
            meta.addSong(extractSong(file, tag, header, fileName, filePath));
            Album album = extractAlbum(tag);
            if (album.getAlbumName() != null) meta.addAlbum(album);
            extractArtists(tag).forEach(meta::addArtist);
            Style style = extractStyle(tag);
            if (style.getStyleName() != null) meta.addStyle(style);
            extractLyrics(tag, filePath).forEach(meta::addLyric);
            return meta;
        } catch (MetadataParseException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析元数据失败: {} - {}", file.getName(), e.getMessage());
            throw new MetadataParseException("解析元数据失败: " + file.getName(), e);
        }
    }


    protected Song extractSong(File file, Tag tag, AudioHeader header, String fileName, String filePath) {
        return Song.builder()
                .fileName(fileName).title(getTitle(tag, fileName))
                .filePath(filePath).fileFormat(AudioFileUtils.extension(file.toPath()))
                .fileSize(file.length()).fileHash(FileHashUtils.getFileHash(file))
                .fileMtime(file.lastModified())
                .createTime(getFileCreateTime(file)).updateTime(getFileModifiedTime(file))
                .duration(safeTrackLength(header)).year(getFirstSafe(tag, FieldKey.YEAR))
                .bitrate(tryParseInt(header.getBitRate())).sampleRate(tryParseInt(header.getSampleRate()))
                .channels(getChannels(header)).bitsPerSample(header.getBitsPerSample())
                .language(getFirstSafe(tag, FieldKey.LANGUAGE)).coverPath(extractCover(tag, filePath))
                .trackNumber(parseFirstInt(tag, FieldKey.TRACK))
                .discNumber(parseFirstInt(tag, FieldKey.DISC_NO))
                .composer(cleanPersonName(getFirstSafe(tag, FieldKey.COMPOSER)))
                .lyricist(cleanPersonName(getFirstSafe(tag, FieldKey.LYRICIST)))
                .build();
    }

    protected Album extractAlbum(Tag tag) {
        String yearStr = getFirstSafe(tag, FieldKey.ALBUM_YEAR);
        if (yearStr == null) yearStr = getFirstSafe(tag, FieldKey.YEAR);
        return Album.builder()
                .albumName(getFirstSafe(tag, FieldKey.ALBUM))
                .albumYear(tryParseInt(yearStr))
                .introduction(getFirstSafe(tag, FieldKey.COMMENT))
                .company(getFirstSafe(tag, FieldKey.RECORD_LABEL))
                .language(getFirstSafe(tag, FieldKey.LANGUAGE))
                .albumType(detectAlbumType(tag))
                .build();
    }

    protected List<Artist> extractArtists(Tag tag) {
        Set<String> names = new LinkedHashSet<>();
                                List<String> multi = safeGetAll(tag, FieldKey.ARTIST);
        if (!multi.isEmpty()) {
            for (String s : multi) {
                                for (String part : parseArtists(s)) {
                    String cleaned = cleanArtistName(part);
                    if (!cleaned.isBlank()) names.add(cleaned);
                }
            }
        }
                if (names.isEmpty()) {
            String single = getFirstSafe(tag, FieldKey.ARTIST);
            if (single != null && !single.isBlank()) {
                for (String part : parseArtists(single)) {
                    String cleaned = cleanArtistName(part);
                    if (!cleaned.isBlank()) names.add(cleaned);
                }
            }
        }
                if (names.isEmpty()) {
            String perf = getFirstSafe(tag, FieldKey.PERFORMER);
            if (perf != null && !perf.isBlank()) {
                String cleaned = cleanArtistName(perf);
                if (!cleaned.isBlank()) names.add(cleaned);
            }
        }
        String country = getFirstSafe(tag, FieldKey.COUNTRY);
        return names.stream().map(n -> Artist.builder().artistName(n).country(country).build())
                .collect(java.util.stream.Collectors.toList());
    }

    private List<String> safeGetAll(Tag tag, FieldKey key) {
        try {
            List<String> all = tag.getAll(key);
            return (all != null) ? all.stream()
                    .filter(v -> v != null && !v.isBlank())
                    .map(String::trim)
                    .toList() : List.of();
        } catch (Exception e) { return List.of(); }
    }

    private String cleanArtistName(String name) {
        if (name == null || name.isBlank()) return "";
        return name.replaceAll("[\\p{Cntrl}]", "").replaceAll("\\s+", " ").trim();
    }

    protected Style extractStyle(Tag tag) {
        return Style.builder().styleName(getStyleName(tag)).build();
    }

    protected List<Lyric> extractLyrics(Tag tag, String filePath) {
        List<Lyric> list = new ArrayList<>();
        String metaLyric = getFirstSafe(tag, FieldKey.LYRICS);
        if (metaLyric != null && !metaLyric.isEmpty())
            list.add(Lyric.builder().content(metaLyric)
                    .lyricHash(FileHashUtils.getFileHash(metaLyric)).type(LyricType.METADATA).build());
        String lrc = findLrcFile(filePath);
        if (lrc != null) {
            try {
                String content = Files.readString(Paths.get(lrc));
                list.add(Lyric.builder().content(content).lrcPath(lrc)
                        .lyricHash(FileHashUtils.getFileHash(content)).type(LyricType.LRC).build());
            } catch (IOException e) { log.warn("LRC读取失败: {}", lrc, e); }
        }
        return list;
    }


    protected final void validateFile(File f) {
        if (!f.exists()) throw new InvalidFileException("文件不存在: " + f.getAbsolutePath());
        if (f.length() < 10) throw new InvalidFileException("文件太小: " + f.getAbsolutePath());
    }

    protected String getTitle(Tag tag, String fileName) {
        String t = getFirstSafe(tag, FieldKey.TITLE);
        if (t != null && !t.trim().isEmpty()) return t.trim();
        if (fileName != null) { int d = fileName.lastIndexOf('.'); return d > 0 ? fileName.substring(0, d) : fileName; }
        return "未知标题";
    }

    protected String getFirstSafe(Tag tag, FieldKey key) {
        if (tag == null) return null;
        try { String v = tag.getFirst(key); return (v != null && !v.isEmpty()) ? v : null; }
        catch (Exception e) { return null; }
    }


    private String cleanPersonName(String v) {
        if (v == null || v.isEmpty()) return null;
        if (v.length() > 200) return null;
        if (v.contains("[ti:") || v.contains("[ar:") || v.contains("[00:")) return null;
        return v;
    }

    private int safeTrackLength(AudioHeader header) {
        try { return header.getTrackLength(); } catch (Exception e) { return 0; }
    }

    protected Integer tryParseInt(String v) {
        if (v == null) return null;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return null; }
    }


    private Integer parseFirstInt(Tag tag, FieldKey key) {
        String v = getFirstSafe(tag, key);
        if (v == null) return null;
        try {
            int slash = v.indexOf('/');
            return Integer.parseInt(slash > 0 ? v.substring(0, slash).trim() : v.trim());
        } catch (NumberFormatException e) { return null; }
    }

    private AlbumType detectAlbumType(Tag tag) {
        if ("1".equals(getFirstSafe(tag, FieldKey.IS_COMPILATION))) return AlbumType.COMPILATION;
        if ("1".equals(getFirstSafe(tag, FieldKey.IS_SOUNDTRACK))) return AlbumType.SOUNDTRACK;
        if ("1".equals(getFirstSafe(tag, FieldKey.IS_LIVE))) return AlbumType.EP;
        return null;
    }

    private Integer getChannels(AudioHeader h) {
        try { return Integer.valueOf(h.getChannels()); } catch (Exception e) {
            try {
                String s = h.getChannels(); if (s == null) return null;
                try { return Integer.parseInt(s); } catch (NumberFormatException ex) {
                    if (s.equalsIgnoreCase("mono")) return 1;
                    if (s.equalsIgnoreCase("stereo") || s.equalsIgnoreCase("Joint Stereo")) return 2;
                    Matcher m = Pattern.compile("(\\d+)\\s*(channels?|ch)").matcher(s);
                    return m.find() ? Integer.parseInt(m.group(1)) : null;
                }
            } catch (Exception ex2) { return null; }
        }
    }

    protected String getRawArtists(Tag tag) {
        try {
            List<String> all = tag.getAll(FieldKey.ARTIST);
            return (all != null && !all.isEmpty()) ? String.join(",", all) : null;
        } catch (Exception e) { return null; }
    }


    protected List<String> parseArtists(String s) {
        if (s == null || s.isBlank()) return List.of();

                if (containsExplicitSeparator(s)) {
                        String n = applyStrongSeparators(s);
            return Arrays.stream(n.split(","))
                    .map(String::trim)
                    .filter(v -> !v.isEmpty())
                    .map(this::normalizeArtist)
                    .filter(v -> !v.isEmpty())
                    .distinct()
                    .toList();
        } else {
                        return smartSpaceSplit(s).stream()
                    .map(this::normalizeArtist)
                    .filter(v -> !v.isEmpty())
                    .distinct()
                    .toList();
        }
    }


    private String applyStrongSeparators(String s) {
        String n = s;
        try {
            String[] entries = JSON_MAPPER.readValue(artistSplitConfig, String[].class);
            for (String entry : entries) {
                if (entry == null || entry.isEmpty()) continue;
                if (" ".equals(entry)) continue;
                if ("\\u0000".equals(entry)) {
                    n = n.replace('\0', ',');
                } else if (entry.length() == 1) {
                    char ch = entry.charAt(0);
                    if (Character.isLetter(ch)) {
                                                n = n.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(entry) + "\\b", ",");
                    } else {
                        n = n.replace(ch, ',');
                    }
                } else {
                    n = n.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(entry) + "\\b", ",");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse artist split config: {}", e.getMessage());
        }
        return n;
    }


    private boolean containsExplicitSeparator(String s) {
        try {
            String[] entries = JSON_MAPPER.readValue(artistSplitConfig, String[].class);
            for (String entry : entries) {
                if (entry == null || entry.isEmpty()) continue;
                if (" ".equals(entry) || "\\u0000".equals(entry)) continue;
                if (entry.length() == 1) {
                    char ch = entry.charAt(0);
                    if (Character.isLetter(ch)) {
                                                if (java.util.regex.Pattern.compile("(?i)\\b" + java.util.regex.Pattern.quote(entry) + "\\b").matcher(s).find())
                            return true;
                    } else {
                        if (s.indexOf(ch) >= 0) return true;
                    }
                } else {
                    if (java.util.regex.Pattern.compile("(?i)\\b" + java.util.regex.Pattern.quote(entry) + "\\b").matcher(s).find())
                        return true;
                }
            }
        } catch (Exception e) {  }
        return false;
    }


    private List<String> smartSpaceSplit(String segment) {
        if (segment == null || segment.isBlank()) return List.of();

                if (containsParentheses(segment)) return List.of(segment);

                if (containsDigit(segment)) return List.of(segment);

                boolean hadSpaces = segment.contains(" ");

                String spaced = insertScriptBoundarySpaces(segment);

        String[] tokens = spaced.split("\\s+");
        if (tokens.length <= 1) return List.of(segment);

                if (allLatinTokens(tokens)) return List.of(segment);

                if (allCJK(tokens)) return List.of(String.join("", tokens));

                        if (!hadSpaces) return List.of(segment);

                return splitByScriptGroup(tokens);
    }


    private static String insertScriptBoundarySpaces(String s) {
        if (s.length() < 2) return s;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (i > 0) {
                char prev = s.charAt(i - 1);
                boolean prevCJK = isCJKChar(prev);
                boolean curCJK = isCJKChar(c);
                boolean prevLatin = isLatinChar(prev);
                boolean curLatin = isLatinChar(c);
                                if ((prevCJK && curLatin) || (prevLatin && curCJK)) {
                    sb.append(' ');
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }


    private List<String> splitByScriptGroup(String[] tokens) {
        List<List<String>> groups = new ArrayList<>();
        List<String> current = new ArrayList<>();
        boolean currentIsLatin = isLatinWord(tokens[0]);

        for (String token : tokens) {
            boolean tokenIsLatin = isLatinWord(token);
            if (tokenIsLatin == currentIsLatin) {
                current.add(token);
            } else {
                groups.add(current);
                current = new ArrayList<>();
                current.add(token);
                currentIsLatin = tokenIsLatin;
            }
        }
        groups.add(current);

        List<String> result = new ArrayList<>();
        for (List<String> group : groups) {
            if (isLatinWord(group.get(0))) {
                result.add(String.join(" ", group));
            } else {
                result.add(String.join("", group));
            }
        }
        return result;
    }


    private static boolean isLatinWord(String s) {
        if (s == null || s.isEmpty()) return false;
        return isLatinChar(s.charAt(0));
    }


    private static boolean isLatinChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }


    private static boolean isCJKChar(char c) {
        return Character.isIdeographic(c);
    }


    private static boolean allLatinTokens(String[] tokens) {
        for (String t : tokens) {
            if (!isLatinWord(t)) return false;
        }
        return true;
    }


    private static boolean allCJK(String[] tokens) {
        for (String t : tokens) {
            if (isLatinWord(t)) return false;
        }
        return true;
    }


    private static boolean containsParentheses(String s) {
        return s.indexOf('(') >= 0 || s.indexOf(')') >= 0
                || s.indexOf('（') >= 0 || s.indexOf('）') >= 0;
    }


    private static boolean containsDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) return true;
        }
        return false;
    }

    private String normalizeArtist(String name) {
        if (name == null || name.isBlank()) return "";
        String c = name.replaceAll("[\\p{Cntrl}\\p{Cc}]", "");
        c = c.replaceAll("\\s*\\([^)]*\\)\\s*", " ");
        c = c.replaceAll("\\s+", " ").trim();
        if (!c.isEmpty() && !c.equals(c.toUpperCase()))
            c = c.substring(0, 1).toUpperCase() + c.substring(1).toLowerCase();
        return c;
    }

    protected String getStyleName(Tag tag) {
        String s = getFirstSafe(tag, FieldKey.GENRE);
        if (s != null && !s.isEmpty()) return s;
        List<String> styles = tag.getAll(FieldKey.GENRE);
        return (styles != null && !styles.isEmpty()) ? styles.getFirst() : "未知风格";
    }


    protected String coversDir = "../covers/";
    protected String musicRootDir = "./music";

    public void setCoversDir(String coversDir) {
        if (coversDir != null && !coversDir.isBlank()) {
            this.coversDir = coversDir;
        }
    }

    public void setMusicRootDir(String musicRootDir) {
        if (musicRootDir != null && !musicRootDir.isBlank()) {
            this.musicRootDir = musicRootDir;
        }
    }


    protected String extractCover(Tag tag, String filePath) {
        try {
            List<Artwork> list = tag.getArtworkList();
            if (list == null || list.isEmpty()) return null;
            byte[] data = list.getFirst().getBinaryData();
            if (data == null || data.length == 0) return null;
            String ext = "jpg";
            String mime = list.getFirst().getMimeType();
            if (mime != null) { if (mime.contains("png")) ext = "png"; else if (mime.contains("gif")) ext = "gif"; }
            File out = coverFileFor(filePath, ext);
            if (!out.exists()) {
                out.getParentFile().mkdirs();
                try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(data); }
            }
                        return java.nio.file.Path.of(coversDir).toAbsolutePath().normalize()
                    .relativize(out.toPath().toAbsolutePath().normalize())
                    .toString().replace('\\', '/');
        } catch (Exception e) { log.warn("封面提取失败: {}", e.getMessage()); return null; }
    }


    protected File coverFileFor(String musicFilePath, String ext) {
        java.nio.file.Path musicRoot = java.nio.file.Path.of(musicRootDir).toAbsolutePath().normalize();
        java.nio.file.Path musicPath = java.nio.file.Path.of(musicFilePath).toAbsolutePath().normalize();
        java.nio.file.Path relative;
        try {
            relative = musicRoot.relativize(musicPath);
        } catch (IllegalArgumentException e) {
            relative = musicPath.getFileName();
        }
        String fileName = relative.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String coverName = (dot > 0 ? fileName.substring(0, dot) : fileName) + "." + ext;
        java.nio.file.Path coverRelative = relative.getParent() != null
                ? relative.getParent().resolve(coverName)
                : java.nio.file.Path.of(coverName);
        return new File(coversDir, coverRelative.toString());
    }

    private String shortHash(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(data);
            StringBuilder sb = new StringBuilder(); for (byte b : h) sb.append(String.format("%02x", b));
            return sb.substring(0, 8);
        } catch (NoSuchAlgorithmException e) { return "0"; }
    }

    protected String findLrcFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) return null;
        try {
            File f = new File(filePath), dir = f.getParentFile();
            if (dir == null) return null;
            String base = f.getName(); int d = base.lastIndexOf('.'); if (d > 0) base = base.substring(0, d);
            File[] files = dir.listFiles(); if (files == null) return null;
            for (File l : files) {
                String n = l.getName(); int dot = n.lastIndexOf('.');
                if (dot > 0 && n.substring(0, dot).equals(base) && n.substring(dot + 1).equalsIgnoreCase("lrc"))
                    return l.getAbsolutePath();
            }
        } catch (Exception e) { log.warn("LRC查找失败: {}", e.getMessage()); }
        return null;
    }

    private LocalDateTime getFileCreateTime(File file) {
        try { return Files.readAttributes(file.toPath(), BasicFileAttributes.class)
                .creationTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (IOException e) { return null; }
    }

    private LocalDateTime getFileModifiedTime(File file) {
        try { return Files.readAttributes(file.toPath(), BasicFileAttributes.class)
                .lastModifiedTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (IOException e) { return null; }
    }


    protected static Map<String, String> extractTxxxMap(AbstractID3v2Tag id3v2) {
        Map<String, String> map = new LinkedHashMap<>();
        List<TagField> txxxFrames = id3v2.getFrame("TXXX");
        if (txxxFrames == null) return map;
        for (TagField f : txxxFrames) {
            try {
                FrameBodyTXXX body = (FrameBodyTXXX) ((AbstractID3v2Frame) f).getBody();
                String desc = body.getDescription();
                String value = body.getText();
                if (desc != null && !desc.isBlank() && value != null && !value.isBlank()) {
                    map.putIfAbsent(desc.trim(), value.trim());
                }
            } catch (Exception e) {  }
        }
        return map;
    }

    protected static String toJson(Map<String, String> map) {
        if (map.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(e.getKey())).append("\":");
            sb.append("\"").append(escapeJson(e.getValue())).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    protected static Map<String, String> parseJson(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return map;
        String content = json.strip();
        if (!content.startsWith("{") || !content.endsWith("}")) return map;
        content = content.substring(1, content.length() - 1);
        for (String pair : content.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
            int colon = pair.indexOf(':');
            if (colon < 0) continue;
            String key = unescapeJson(pair.substring(0, colon).strip());
            String value = unescapeJson(pair.substring(colon + 1).strip());
            if (key.startsWith("\"") && key.endsWith("\"")) key = key.substring(1, key.length() - 1);
            if (value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
            if (!key.isEmpty() && !value.isEmpty()) map.putIfAbsent(key, value);
        }
        return map;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String unescapeJson(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\")
                .replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
    }
}
