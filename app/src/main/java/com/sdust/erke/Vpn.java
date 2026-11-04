package com.sdust.erke;

import android.content.SharedPreferences;

/**
 * webVPN 会话辅助：记录 Cookie 保存时间，判断“是否快过期”，供启动时提前静默续期。
 *
 * 说明：webVPN 的 wengine 票据 cookie 是会话型，客户端读不到服务端真实过期时间，
 * 这里用「保存时长」做一个保守估算；真正的有效性仍以接口是否被 403 拦截为准
 * （拦截时登录页/主页会自动触发静默刷新兜底）。
 */
public final class Vpn {
    /** 估算的 webVPN 会话有效期（8 小时）。 */
    public static final long TTL_MS = 8L * 3600 * 1000;
    /** 提前刷新窗口（到期前 2 小时就开始续期）。 */
    public static final long AHEAD_MS = 2L * 3600 * 1000;

    private Vpn() {}

    public static boolean on(SharedPreferences sp) {
        return sp.getBoolean("use_vpn", false);
    }

    public static String cookie(SharedPreferences sp) {
        String c = sp.getString("vpn_cookie", "");
        return c == null ? "" : c;
    }

    public static long savedAt(SharedPreferences sp) {
        return sp.getLong("vpn_cookie_time", 0L);
    }

    public static void markSaved(SharedPreferences sp) {
        sp.edit().putLong("vpn_cookie_time", System.currentTimeMillis()).apply();
    }

    public static boolean hasCreds(SharedPreferences sp) {
        return !sp.getString("user", "").isEmpty() && !sp.getString("pwd", "").isEmpty();
    }

    /** 是否需要（提前）静默刷新：已开启 VPN；没 cookie、没时间戳、或已进入提前刷新窗口。 */
    public static boolean needsRefresh(SharedPreferences sp) {
        if (!on(sp)) return false;
        if (cookie(sp).isEmpty()) return true;
        long t = savedAt(sp);
        if (t <= 0L) return true;
        return System.currentTimeMillis() - t >= TTL_MS - AHEAD_MS;
    }

    /** 剩余有效时间的人话描述，用于界面状态显示。 */
    public static String statusText(SharedPreferences sp) {
        if (!on(sp)) return "webVPN：未开启（校园网可直连，更快）";
        if (cookie(sp).isEmpty()) return "webVPN：已开启，尚未登录";
        long t = savedAt(sp);
        if (t <= 0L) return "webVPN：已登录";
        long left = TTL_MS - (System.currentTimeMillis() - t);
        if (left <= 0) return "webVPN：会话可能已过期，将自动续期";
        long h = left / 3600000L, m = (left % 3600000L) / 60000L;
        return "webVPN：已登录 · 约 " + h + " 小时 " + m + " 分后需续期";
    }
}
