package com.sdust.erke;

import android.util.Log;
import android.webkit.CookieManager;

import org.json.JSONObject;

/** 从 WebView 读取 CAS 会话 Cookie，供原生网络层复用，避免再次输入账号密码。 */
final class CasCookies {
    private static final String[] HOSTS = {"my.sdust.edu.cn", "cas.sdust.edu.cn"};

    private CasCookies() {}

    static JSONObject collect() {
        JSONObject map = new JSONObject();
        try {
            for (String host : HOSTS) {
                // 按 /cas/login 取，才能拿到 path=/cas 的 TGC / JSESSIONID
                String ck = CookieManager.getInstance().getCookie("https://" + host + "/cas/login");
                if (ck != null && !ck.isEmpty()) map.put(host, ck);
                Log.i("ErkeVpn", "CasCookies.collect: " + host + " len=" + (ck == null ? 0 : ck.length()));
            }
        } catch (Exception e) {
            Log.e("ErkeVpn", "CasCookies.collect failed", e);
        }
        return map;
    }
}
