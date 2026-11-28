package com.sdust.erke;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 只调用平台 API 的客户端：CAS 统一认证登录 + 各类接口。 */
public class Api {
    public static final String API_BASE = "https://ekta.sdust.edu.cn/api/app/client/v1/";
    public static final String CAS_BASE = "https://my.sdust.edu.cn/cas";
    public static final String SSO_TARGET = "https://ekta.sdust.edu.cn/sdustlogin";
    public static final int SCHOOL_CODE = 10424;

    public static final String VPN_PREFIX = "https://webvpn.sdust.edu.cn/https/77726476706e69737468656265737421f5fc559d69236c456d1cc7a99c406d3661";

    private String token;
    private boolean useVpn = false;
    private String vpnCookie = "";

    public void setVpn(boolean v, String cookie) {
        useVpn = v;
        vpnCookie = cookie == null ? "" : cookie;
    }
    public boolean isVpn() { return useVpn; }
    private String base() {
        return useVpn ? (VPN_PREFIX + "/api/app/client/v1/") : API_BASE;
    }

    static {
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
    }

    public String getToken() { return token; }
    public void setToken(String t) { token = t; }

    static class Resp { int code; String location; String body; }

    Resp http(String method, String url, String body, Map<String, String> headers,
              boolean follow, int maxHops) throws Exception {
        String cur = url, methodCur = method, bodyCur = body;
        Map<String, String> hCur = headers;
        for (int hop = 0; hop <= maxHops; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(cur).openConnection();
            c.setRequestMethod(methodCur);
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)");
            c.setRequestProperty("Accept", "application/json, text/plain, */*");
            if (useVpn && !vpnCookie.isEmpty() && cur.contains("webvpn.sdust.edu.cn")) {
                c.setRequestProperty("Cookie", vpnCookie);
            }
            if (hCur != null) for (Map.Entry<String, String> e : hCur.entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
            if (bodyCur != null) c.setDoOutput(true);
            c.connect();
            if (bodyCur != null) {
                try (OutputStream os = c.getOutputStream()) {
                    os.write(bodyCur.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = c.getResponseCode();
            String loc = c.getHeaderField("Location");
            InputStream is = (code >= 400) ? c.getErrorStream() : c.getInputStream();
            String text = "";
            if (is != null) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                text = bo.toString("UTF-8");
            }
            c.disconnect();
            try {
                android.util.Log.i("ErkeApi", methodCur + " " + cur + " -> " + code + " len=" + text.length()
                        + " head=" + text.substring(0, Math.min(120, text.length())).replace("\n", " "));
            } catch (Exception ignored) {}
            if (follow && code >= 300 && code < 400 && loc != null) {
                cur = new URL(new URL(cur), loc).toString();
                methodCur = "GET";
                bodyCur = null;
                hCur = null;
                continue;
            }
            Resp r = new Resp();
            r.code = code; r.location = loc; r.body = text;
            return r;
        }
        throw new IOException("too many redirects");
    }

    static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    JSONObject apiGet(String path, JSONObject params) throws Exception {
        String js = (params == null) ? "{}" : params.toString();
        String url = base() + path + "?params=" + enc(js);
        Map<String, String> h = new HashMap<>();
        if (token != null) h.put("Authorization", token);
        Resp r = http("GET", url, null, h, true, 5);
        return jsonOrThrow(r.body, "GET " + path);
    }

    static JSONObject jsonOrThrow(String body, String ctx) throws Exception {
        String t = body == null ? "" : body.trim();
        if (!t.startsWith("{")) {
            String vis = t.replaceAll("(?is)<script.*?</script>", " ")
                    .replaceAll("(?is)<style.*?</style>", " ")
                    .replaceAll("<[^>]+>", " ")
                    .replaceAll("\\s+", " ").trim();
            throw new IOException(ctx + " 返回网页: " + vis.substring(0, Math.min(300, vis.length())));
        }
        return new JSONObject(t);
    }

    JSONObject apiPost(String path, JSONObject params) throws Exception {
        String js = (params == null) ? "{}" : params.toString();
        String body = "params=" + enc(js);
        Map<String, String> h = new HashMap<>();
        h.put("Content-Type", "application/x-www-form-urlencoded");
        if (token != null) h.put("Authorization", token);
        Resp r = http("POST", base() + path, body, h, true, 5);
        return jsonOrThrow(r.body, "POST " + path);
    }

    /** 与网页端一致：密码倒序后做教科书式 RSA(小端整数 pow(e,m))，输出 hex。 */
    static String rsaEncrypt(String s, String eHex, String mHex) {
        BigInteger e = new BigInteger(eHex, 16);
        BigInteger m = new BigInteger(mHex, 16);
        int digits = (m.bitLength() + 15) / 16;
        int chunk = 2 * (digits - 1);
        int total = ((s.length() + chunk - 1) / chunk) * chunk;
        byte[] all = new byte[total];
        for (int i = 0; i < s.length(); i++) all[i] = (byte) s.charAt(i);
        StringBuilder sb = new StringBuilder();
        for (int off = 0; off < total; off += chunk) {
            byte[] be = new byte[chunk];
            for (int i = 0; i < chunk; i++) be[i] = all[off + chunk - 1 - i];
            BigInteger blk = new BigInteger(1, be);
            if (sb.length() > 0) sb.append(' ');
            sb.append(blk.modPow(e, m).toString(16));
        }
        return sb.toString();
    }

    public String login(String user, String pwd) throws Exception {
        String cur = CAS_BASE + "/login?service=" + enc(SSO_TARGET);
        Resp page = http("GET", cur, null, null, false, 0);
        String html = page.body == null ? "" : page.body;
        String ticketUrl = page.location;
        String ticket = extractTicket(ticketUrl);
        // CAS 已有登录态时会直接 302 到 service 并带上 ticket，也可能先跳到其他登录地址
        for (int hop = 0; ticket == null && page.code >= 300 && page.code < 400
                && ticketUrl != null && hop < 5; hop++) {
            cur = new URL(new URL(cur), ticketUrl).toString();
            page = http("GET", cur, null, null, false, 0);
            html = page.body == null ? "" : page.body;
            ticketUrl = page.location;
            ticket = extractTicket(ticketUrl);
        }
        if (ticket == null) ticket = extractTicket(html);
        if (ticket == null) {
            Resp pk = http("GET", CAS_BASE + "/v2/getPubKey", null, null, false, 0);
            JSONObject jpk = jsonOrThrow(pk.body, "getPubKey");
            String mod = jpk.getString("modulus"), exp = jpk.getString("exponent");
            Matcher m = Pattern.compile("<form[^>]*id=\"fm1\"[^>]*action=\"([^\"]*)\"").matcher(html);
            String action = m.find() ? m.group(1).trim() : "login";
            Matcher me = Pattern.compile("name=\"execution\"\\s+value=\"([^\"]+)\"").matcher(html);
            String execution = me.find() ? me.group(1) : "";
            StringBuilder rev = new StringBuilder(pwd);
            rev.reverse();
            String encPwd = rsaEncrypt(rev.toString(), exp, mod);
            String postUrl = new URL(new URL(cur), action).toString();
            String form = "username=" + enc(user) + "&password=" + enc(encPwd) + "&execution=" + enc(execution)
                    + "&_eventId=submit&rememberMe=true";
            Map<String, String> h = new HashMap<>();
            h.put("Content-Type", "application/x-www-form-urlencoded");
            Resp post = http("POST", postUrl, form, h, false, 0);
            ticketUrl = post.location;
            ticket = extractTicket(ticketUrl);
            if (ticket == null) ticket = extractTicket(post.body);
        }
        if (ticket == null) throw new IOException("CAS 登录失败（未取得 ticket），请检查账号密码或稍后重试");
        JSONObject sp = new JSONObject();
        sp.put("targetUrl", SSO_TARGET);
        sp.put("ticket", ticket);
        sp.put("routeUrl", "/sdustlogin");
        sp.put("pathAll", ticketUrl != null ? ticketUrl : SSO_TARGET);
        JSONObject d = apiGet("user/sdustSSOAuth", sp);
        if (d.optInt("code", -1) != 0) throw new IOException("SSO 校验失败: " + d.optString("msg"));
        JSONObject dd = d.getJSONObject("data");
        JSONObject tk = new JSONObject();
        tk.put("schoolCode", SCHOOL_CODE);
        tk.put("code", dd.optString("code"));
        tk.put("timestamp", dd.optLong("timestamp"));
        tk.put("sign", dd.optString("sign"));
        JSONObject tr = apiGet("third/token", tk);
        if (tr.optInt("code", -1) != 0) throw new IOException("换取 token 失败: " + tr.optString("msg"));
        token = tr.getJSONObject("data").getString("access_token");
        return token;
    }

    static String extractTicket(String s) {
        if (s == null) return null;
        Matcher m = Pattern.compile("[?&]ticket=([^&#\\s\"']+)").matcher(s);
        if (m.find()) {
            try { return URLDecoder.decode(m.group(1), "UTF-8"); } catch (Exception e) { return m.group(1); }
        }
        return null;
    }

    public JSONObject myInfo() throws Exception { return apiGet("student/user/my-info", new JSONObject()); }

    /** 按活动种类统计学时（每项含 classifyName / classifyHours 已获 / minHours 要求）。 */
    public JSONArray achievementByClassify() throws Exception {
        JSONObject r = apiGet("student/achievement/by-classify-list", new JSONObject());
        if (r.optInt("code", -1) != 0) throw new IOException(r.optString("msg"));
        JSONArray a = r.optJSONArray("data");
        return a == null ? new JSONArray() : a;
    }

    public JSONArray listActivities(String status) throws Exception {
        JSONObject p = new JSONObject();
        p.put("pageNum", 1); p.put("pageSize", 200); p.put("sortType", 1);
        p.put("status", status); p.put("fieldType", "activity_iseErollment"); p.put("keyWord", "switch");
        JSONObject r = apiGet("page/activity/list", p);
        if (r.optInt("code", -1) != 0) throw new IOException(r.optString("msg"));
        JSONObject data = r.optJSONObject("data");
        JSONArray arr = data == null ? null : data.optJSONArray("list");
        return arr == null ? new JSONArray() : arr;
    }

    public JSONObject detail(int id) throws Exception {
        JSONObject p = new JSONObject();
        p.put("id", String.valueOf(id));
        JSONObject r = apiGet("activity/detail/participant", p);
        if (r.optInt("code", -1) != 0) throw new IOException(r.optString("msg"));
        return r.getJSONObject("data");
    }

    /**
     * 活动签到统计（返回完整响应：data 为全场签到数，timestamp 为服务器时间戳）。
     * 服务器时间戳用于生成签到二维码里的 sp，避免手机时钟偏差导致工作人员扫码失败。
     */
    public JSONObject signCount(int id) throws Exception {
        JSONObject p = new JSONObject();
        p.put("id", id);
        JSONObject r = apiGet("activity/sign/count", p);
        if (r.optInt("code", -1) != 0) throw new IOException(r.optString("msg"));
        return r;
    }

    public JSONObject enroll(int id, JSONArray personMaterial) throws Exception {
        JSONObject p = new JSONObject();
        p.put("id", id);
        p.put("personMaterial", personMaterial == null ? new JSONArray() : personMaterial);
        return apiPost("activity/enroll/person", p);
    }

    public JSONObject cancel(int id, String applyInfo) throws Exception {
        JSONObject p = new JSONObject();
        p.put("id", id);
        p.put("applyInfo", applyInfo == null ? "" : applyInfo);
        return apiPost("activity/enroll/cancel/person", p);
    }

    /** 我报名的活动（applyStatus=2 已报名；status 5报名中/6进行中/7已结束）。 */
    public JSONObject myList() throws Exception {
        JSONObject p = new JSONObject();
        p.put("pageNum", 1); p.put("pageSize", 60);
        p.put("status", "4,5,6,7,8"); p.put("applyStatus", "2");
        JSONObject r = apiGet("activity/my-list", p);
        if (r.optInt("code", -1) != 0) throw new IOException(r.optString("msg"));
        JSONObject d = r.optJSONObject("data");
        return d == null ? new JSONObject() : d;
    }

    /** 签到/签退：type=2 常规（signSwitch 1=签到 2=签退），type=1 候场(waitSign)。 */
    public JSONObject signInOut(int id, int userId, int type, String sp, Integer signSwitch) throws Exception {
        JSONObject p = new JSONObject();
        p.put("id", id);
        p.put("userId", userId);
        p.put("type", type);
        p.put("sp", sp == null ? "" : sp);
        if (signSwitch != null) p.put("signSwitch", signSwitch.intValue());
        return apiPost("activity/sign/in-out", p);
    }

    /** 某活动的打卡（签到/签退）记录。 */
    public JSONArray signRecords(int userId, int activityId) throws Exception {
        JSONObject p = new JSONObject();
        p.put("userId", userId);
        p.put("activityId", activityId);
        JSONObject r = apiPost("activity/sign/one/list", p);
        if (r.optInt("code", -1) != 0) throw new IOException(r.optString("msg"));
        JSONArray a = r.optJSONArray("data");
        return a == null ? new JSONArray() : a;
    }

    /** GPS 定位签到/签退（signWay 含 4/5 的活动）。 */
    public JSONObject mapSign(int id, double longitude, double latitude, String address, long sp) throws Exception {
        JSONObject p = new JSONObject();
        p.put("id", id);
        p.put("sp", sp);
        p.put("longitude", longitude);
        p.put("latitude", latitude);
        p.put("address", address == null ? "" : address);
        return apiPost("activity/sign/map-in-out", p);
    }
}
