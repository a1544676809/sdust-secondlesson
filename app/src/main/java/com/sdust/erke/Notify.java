package com.sdust.erke;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 统一的通知入口：发系统通知 + 持久化记录到应用内“消息中心”。
 *
 * 持久化要点（本轮补充）：
 *  - 消息历史存放在独立的 SharedPreferences 文件 "erke_messages"，与登录/设置分离，不会被“退出登录”等清掉；
 *  - 使用 commit() 同步落盘，确保 App 被划掉 / 强杀 / 突然退出也不丢消息；
 *  - 先写历史、再发系统通知（notify 失败也不影响历史保存）；
 *  - 保留最近 200 条；支持未读数、全部已读；兼容旧版 "erke/notifications" 数据的自动迁移。
 */
public class Notify {
    public static final String CH_GRAB = "ch_grab_events";
    public static final String CH_REMIND = "ch_remind";
    public static final String ACTION_HISTORY = "com.sdust.erke.HISTORY";

    private static final String PREF_MSGS = "erke_messages";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_LAST_READ = "lastReadTs";
    private static final int MAX_KEEP = 200;

    static SharedPreferences store(Context c) {
        return c.getSharedPreferences(PREF_MSGS, Context.MODE_PRIVATE);
    }

    static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm.getNotificationChannel(CH_GRAB) == null) {
                    nm.createNotificationChannel(new NotificationChannel(CH_GRAB, "抢活动结果", NotificationManager.IMPORTANCE_HIGH));
                }
                if (nm.getNotificationChannel(CH_REMIND) == null) {
                    nm.createNotificationChannel(new NotificationChannel(CH_REMIND, "活动提醒", NotificationManager.IMPORTANCE_HIGH));
                }
            } catch (Throwable ignored) {}
        }
    }

    /** 发一条通知：先持久化历史，再尝试弹系统通知。 */
    public static void post(Context c, String title, String text, int activityId, boolean grabEvent) {
        // 1) 先落盘（即使后面系统通知失败，历史也不会丢）
        append(c, title, text, activityId);
        // 2) 再发系统通知（尽力而为）
        try {
            ensureChannels(c);
            Intent it = new Intent(c, activityId > 0 ? ActivityDetailActivity.class : MainActivity.class);
            if (activityId > 0) it.putExtra("id", activityId);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int req = (int) (System.currentTimeMillis() % 100000);
            PendingIntent pi = PendingIntent.getActivity(c, req, it,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                    ? new Notification.Builder(c, grabEvent ? CH_GRAB : CH_REMIND)
                    : new Notification.Builder(c);
            b.setContentTitle(title).setContentText(text)
                    .setSmallIcon(android.R.drawable.stat_notify_more)
                    .setAutoCancel(true).setContentIntent(pi);
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(req, b.build());
        } catch (Throwable ignored) {}
    }

    static void append(Context c, String title, String text, int activityId) {
        try {
            SharedPreferences sp = store(c);
            JSONArray a = history(c);
            JSONObject o = new JSONObject();
            o.put("t", title);
            o.put("m", text);
            o.put("id", activityId);
            o.put("ts", System.currentTimeMillis());
            a.put(o);
            while (a.length() > MAX_KEEP) a.remove(0);
            // 同步提交，保证落盘
            sp.edit().putString(KEY_HISTORY, a.toString()).commit();
            broadcast(c);
        } catch (Throwable ignored) {}
    }

    /** 读取全部历史（旧版存在 "erke"/"notifications" 的会自动迁移过来）。 */
    public static JSONArray history(Context c) {
        try {
            SharedPreferences sp = store(c);
            JSONArray a = new JSONArray(sp.getString(KEY_HISTORY, "[]"));
            if (a.length() == 0) {
                SharedPreferences old = c.getSharedPreferences("erke", Context.MODE_PRIVATE);
                String oldv = old.getString("notifications", null);
                if (oldv != null && !oldv.equals("[]") && !oldv.isEmpty()) {
                    try {
                        a = new JSONArray(oldv);
                        sp.edit().putString(KEY_HISTORY, a.toString()).commit();
                        old.edit().remove("notifications").commit();
                    } catch (Throwable ignored) {}
                }
            }
            return a;
        } catch (Throwable e) {
            return new JSONArray();
        }
    }

    public static void clear(Context c) {
        try {
            store(c).edit().putString(KEY_HISTORY, "[]")
                    .putLong(KEY_LAST_READ, System.currentTimeMillis()).commit();
        } catch (Throwable ignored) {}
        broadcast(c);
    }

    /** 未读条数（ts 晚于最近一次“已读”时间）。 */
    public static int unread(Context c) {
        int n = 0;
        try {
            long lastRead = store(c).getLong(KEY_LAST_READ, 0L);
            JSONArray a = history(c);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null && o.optLong("ts", 0L) > lastRead) n++;
            }
        } catch (Throwable ignored) {}
        return n;
    }

    /** 标记全部已读。 */
    public static void markAllRead(Context c) {
        try {
            long lastRead = System.currentTimeMillis();
            JSONArray a = history(c);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) lastRead = Math.max(lastRead, o.optLong("ts", 0L));
            }
            store(c).edit().putLong(KEY_LAST_READ, lastRead).commit();
        } catch (Throwable ignored) {}
        broadcast(c);
    }

    static void broadcast(Context c) {
        try {
            Intent u = new Intent(ACTION_HISTORY);
            u.setPackage(c.getPackageName());
            c.sendBroadcast(u);
        } catch (Throwable ignored) {}
    }
}
