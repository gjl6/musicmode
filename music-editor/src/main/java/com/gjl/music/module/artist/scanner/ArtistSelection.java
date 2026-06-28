package com.gjl.music.module.artist.scanner;

import java.util.List;

/**
 * 艺术家选择规则 —— 前端传给 artist-scanner 源模块，描述要处理哪些艺术家。
 *
 * <p>选择模式：
 * <ul>
 *   <li>{@code all} — 全部艺术家</li>
 *   <li>{@code letter} — 首字母过滤（A-Z, 0-9, #）</li>
 *   <li>{@code keyword} — 名称模糊搜索</li>
 *   <li>{@code incomplete} — 信息不完整（缺封面/简介/国家/性别）</li>
 *   <li>{@code unenriched} — 从未被在线源增强过</li>
 *   <li>{@code nonstandard} — 名称包含不规范字符（全角/连续空格/feat.分隔符）</li>
 *   <li>{@code naked} — 裸数据，仅姓名其余全空</li>
 *   <li>{@code duplicates} — 存在大小写/别名重复</li>
 *   <li>{@code ids} — 手动指定 ID 列表</li>
 * </ul>
 *
 * <p>叠加过滤（所有 mode 通用）：style, country, minSongs, maxSongs。
 */
public class ArtistSelection {

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

    /** 最少歌曲数（>= N） */
    private Integer minSongs;

    /** 最多歌曲数（<= N） */
    private Integer maxSongs;

    /** 流派过滤（style 名称精确匹配） */
    private String style;

    /** 国家过滤（country 精确匹配） */
    private String country;

    // ── Getters / Setters ──

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getLetter() { return letter; }
    public void setLetter(String letter) { this.letter = letter; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }

    public Integer getMinSongs() { return minSongs; }
    public void setMinSongs(Integer minSongs) { this.minSongs = minSongs; }

    public Integer getMaxSongs() { return maxSongs; }
    public void setMaxSongs(Integer maxSongs) { this.maxSongs = maxSongs; }

    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    /** 验证 mode 是否有效 */
    public boolean isValid() {
        if (mode == null || mode.isBlank()) return false;
        return switch (mode) {
            case "all", "letter", "keyword", "incomplete", "unenriched",
                 "nonstandard", "naked", "duplicates", "ids" -> true;
            default -> false;
        };
    }
}
