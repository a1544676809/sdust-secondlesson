package com.sdust.erke;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** 本地收藏：只存 SharedPreferences 的活动快照，不依赖服务器。 */
public final class Favorites {
    private static final String KEY = "favorites";

    private Favorites() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("erke", Context.MODE_PRIVATE);
    }

    private static void save(Context c, JSONArray arr) {
        sp(c).edit().putString(KEY, arr.toString()).apply();
    }

    public static JSONArray list(Context c) {
        try {
            String s = sp(c).getString(KEY, "[]");
            return new JSONArray(s == null || s.trim().isEmpty() ? "[]" : s);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static boolean contains(Context c, int id) {
        JSONArray arr = list(c);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && o.optInt("id") == id) return true;
        }
        return false;
    }

    /** 从活动详情快照出要保存的字段。 */
    public static JSONObject snapshot(JSONObject detail, int id) {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id > 0 ? id : detail.optInt("id"));
            o.put("name", detail.optString("name"));
            o.put("status", detail.optInt("status"));
            o.put("classifyName", detail.optString("classifyName"));
            o.put("organizationName", detail.optString("organizationName"));
            o.put("address", detail.optString("address"));
            o.put("hours", detail.optString("hours"));
            o.put("enrollStartTime", detail.optLong("enrollStartTime"));
            o.put("enrollEndTime", detail.optLong("enrollEndTime"));
            o.put("startTime", detail.optLong("startTime"));
            o.put("endTime", detail.optLong("endTime"));
            o.put("peopleLimit", detail.optInt("peopleLimit"));
            o.put("enrollCount", detail.optInt("joinMemberCount"));
            o.put("savedAt", System.currentTimeMillis());
        } catch (Exception ignored) {}
        return o;
    }

    public static void add(Context c, JSONObject snap) {
        int id = snap.optInt("id");
        JSONArray old = list(c);
        JSONArray arr = new JSONArray();
        arr.put(snap);
        for (int i = 0; i < old.length(); i++) {
            JSONObject o = old.optJSONObject(i);
            if (o != null && o.optInt("id") != id) arr.put(o);
        }
        save(c, arr);
    }

    public static void remove(Context c, int id) {
        JSONArray old = list(c);
        JSONArray arr = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject o = old.optJSONObject(i);
            if (o != null && o.optInt("id") != id) arr.put(o);
        }
        save(c, arr);
    }

    /** 若已收藏，用最新详情覆盖（状态/时间/名额变化），返回是否已收藏。 */
    public static boolean refreshIfSaved(Context c, JSONObject detail, int id) {
        if (!contains(c, id)) return false;
        add(c, snapshot(detail, id));
        return true;
    }

    /** 切换收藏状态，返回切换后是否为已收藏。 */
    public static boolean toggle(Context c, JSONObject detail, int id) {
        if (contains(c, id)) {
            remove(c, id);
            return false;
        }
        add(c, snapshot(detail, id));
        return true;
    }
}
