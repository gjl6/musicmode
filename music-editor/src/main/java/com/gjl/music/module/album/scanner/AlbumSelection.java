package com.gjl.music.module.album.scanner;

import java.util.List;

/**
 * 专辑选择规则 —— 前端传给 album-scanner 源模块，描述要处理哪些专辑。
 *
 * <p>选择模式：
 * <ul>
 *   <li>{@code all} — 全部专辑</li>
 *   <li>{@code letter} — 首字母过滤（A-Z, 0-9, #）</li>
 *   <li>{@code keyword} — 名称模糊搜索</li>
 *   <li>{@code incomplete} — 信息不完整（缺封面/简介/类型/年份/公司/语言）</li>
 *   <li>{@code unenriched} — 从未被在线源增强过</li>
 *   <li>{@code naked} — 裸数据，仅名称其余全空</li>
 *   <li>{@code duplicates} — 存在大小写/别名重复</li>
 *   <li>{@code ids} — 手动指定 ID 列表</li>
 * </ul>
 *
 * <p>叠加过滤（所有 mode 通用）：artistId, minSongs, maxSongs。
 *
 * @see com.gjl.music.module.artist.scanner.ArtistSelection
 */
public class AlbumSelection {

    /** 主选择模式 */
    private String mode;

    // ── 模式参数 ──

    /** mode=letter 时的首字母 */
    private String letter;

    /** mode=keyword 时的搜索关键词 */
    private String keyword;

    /** mode=ids 时的指定 ID 列表 */
    private List<Long> ids;

    // ── 叠加过滤（所有 mode 可选）──

    /** 按艺术家 ID 过滤 */
    private Long artistId;

    /** 最少歌曲数（>= N） */
    private Integer minSongs;

    /** 最多歌曲数（<= N） */
    private Integer maxSongs;

    // ── Getters / Setters ──

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getLetter() { return letter; }
    public void setLetter(String letter) { this.letter = letter; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }

    public Long getArtistId() { return artistId; }
    public void setArtistId(Long artistId) { this.artistId = artistId; }

    public Integer getMinSongs() { return minSongs; }
    public void setMinSongs(Integer minSongs) { this.minSongs = minSongs; }

    public Integer getMaxSongs() { return maxSongs; }
    public void setMaxSongs(Integer maxSongs) { this.maxSongs = maxSongs; }

    /** 验证 mode 是否有效 */
    public boolean isValid() {
        if (mode == null || mode.isBlank()) return false;
        return switch (mode) {
            case "all", "letter", "keyword", "incomplete", "unenriched",
                 "naked", "duplicates", "ids" -> true;
            default -> false;
        };
    }
}
