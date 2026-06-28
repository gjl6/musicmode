package com.gjl.music.module;

/**
 * 闊充箰鏁版嵁婧愬叕鍏卞熀鎺ュ彛 鈥?浠呭０鏄庢爣璇嗗拰鍋ュ悍妫€鏌ヨ兘鍔涖€? *
 * <p>瀹炵幇绫诲繀椤绘槸绾跨▼瀹夊叏鐨勶紙鍙兘琚涓閬?worker 骞跺彂璋冪敤锛夈€? * 鍏蜂綋鏌ヨ鑳藉姏鐢卞瓙鎺ュ彛 {@link SongProvider}锛堟瓕鏇叉悳绱級鍜? * {@link ArtistProvider}锛堣壓鏈璇︽儏锛夊０鏄庛€? */
public interface MusicProvider {

    /** 鍞竴鏍囪瘑锛屽 "qqmusic"銆?kugou"銆?kuwo"銆?netease" */
    String name();

    /** 鍙嬪ソ灞曠ず鍚嶇О锛岄粯璁ゅ悓 {@link #name()} */
    default String label() { return name(); }

    /** 鍋ュ悍妫€鏌ワ紙蹇€熻繛閫氭€ф祴璇曪級锛岄粯璁よ繑鍥?true */
    default boolean healthCheck() { return true; }
}
