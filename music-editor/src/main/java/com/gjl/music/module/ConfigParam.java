package com.gjl.music.module;

import java.lang.annotation.*;

/**
 * 鑷畾涔夋爣绛炬簮鍙厤缃弬鏁?鈥斺€?鏍囪鍦?{@link AbstractCustomProvider} 瀛愮被鐨勫瓧娈典笂銆? * 绯荤粺涓婁紶鏃惰嚜鍔ㄦ彁鍙栧埌 DB锛屽墠绔厤缃晫闈㈣嚜鍔ㄦ覆鏌撱€? */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConfigParam {

    /** 鍓嶇鏄剧ず鍚?*/
    String label();

    /** 鍊肩被鍨嬶細STRING / INT / BOOLEAN */
    String type() default "STRING";

    /** 鏄惁蹇呭～ */
    boolean required() default false;

    /** 鏄惁鏁忔劅锛堝墠绔樉绀轰负 ****锛?*/
    boolean sensitive() default false;
}
