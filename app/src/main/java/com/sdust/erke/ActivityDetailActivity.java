package com.sdust.erke;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.MimeTypeMap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 活动详情页。 */
public class ActivityDetailActivity extends Activity {
    private SharedPreferences sp;
    private Api api = new Api();
    private int id;
    private String name = "";
    private boolean dark = false;
    private int accent = 0xFF0B8F6A;
    private int myUserId = 0;
    private JSONObject detail;
    private LinearLayout content;
    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private Runnable countdownTick;
    private TextView countdownText;
    private TextView topEnroll;
    private TextView favBtn;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("erke", MODE_PRIVATE);
        dark = sp.getBoolean("dark", false);
        accent = sp.getInt("accent", 0xFF0B8F6A);
        api.setToken(sp.getString("token", ""));
        api.setVpn(sp.getBoolean("use_vpn", false), sp.getString("vpn_cookie", ""));
        id = getIntent().getIntExtra("id", 0);
        name = getIntent().getStringExtra("name");
        buildUi();
        load();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private int cBg() { return dark ? 0xFF121417 : 0xFFF2F4F7; }
    private int cCard() { return dark ? 0xFF1E2228 : 0xFFFFFFFF; }
    private int cText() { return dark ? 0xFFECECEC : 0xFF222222; }
    private int cSub() { return dark ? 0xFF9AA0A6 : 0xFF777777; }
    private int cAccent() { return accent; }

    private GradientDrawable bg(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg());
        setContentView(root);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(12), dp(14), dp(10));
        bar.setBackgroundColor(cCard());
        TextView back = new TextView(this);
        back.setText("‹ 返回");
        back.setTextSize(15); back.setTextColor(cAccent());
        back.setPadding(0, dp(4), dp(14), dp(4));
        back.setOnClickListener(v -> finish());
        TextView title = new TextView(this);
        title.setText("活动详情");
        title.setTextSize(17); title.setTextColor(cText()); title.setTypeface(null, Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView refresh = new TextView(this);
        refresh.setText("刷新");
        refresh.setTextSize(14); refresh.setTextColor(cAccent());
        refresh.setPadding(dp(14), dp(6), dp(14), dp(6));
        refresh.setBackground(bg(0x1A000000 | (accent & 0xFFFFFF), 16, 0, 0));
        refresh.setOnClickListener(v -> {
            if (loading) return;
            toast("刷新中…");
            load();
        });
        topEnroll = new TextView(this);
        topEnroll.setText("立即抢");
        topEnroll.setTextSize(14); topEnroll.setTextColor(0xFFFFFFFF);
        topEnroll.setTypeface(null, Typeface.BOLD);
        topEnroll.setPadding(dp(14), dp(6), dp(14), dp(6));
        topEnroll.setBackground(bg(cAccent(), 16, 0, 0));
        topEnroll.setOnClickListener(v -> manualEnroll());
        topEnroll.setVisibility(View.GONE);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.rightMargin = dp(8);
        topEnroll.setLayoutParams(tlp);
        bar.addView(back); bar.addView(title); bar.addView(topEnroll); bar.addView(refresh);
        root.addView(bar);

        ScrollView sc = new ScrollView(this);
        sc.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(12));
        sc.addView(content);
        root.addView(sc);

        TextView loading = new TextView(this);
        loading.setText("加载中…");
        loading.setTextColor(cSub());
        loading.setPadding(dp(4), dp(24), 0, 0);
        content.addView(loading);
    }

    private static String fmt(long ms) {
        if (ms <= 0) return "-";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(ms));
    }
    private static String stName(int s) {
        switch (s) {
            case 4: return "报名未开始"; case 5: return "报名中"; case 6: return "待开始";
            case 7: return "进行中"; case 8: return "已结束"; default: return "状态" + s;
        }
    }
    private int stColor(int s) {
        switch (s) { case 4: return 0xFFE6A23C; case 5: return 0xFF0B8F6A; case 6: return 0xFFE6A23C; case 7: return 0xFF409EFF; case 8: return 0xFF909399; default: return 0xFF909399; }
    }

    private void load() {
        if (loading) return;
        loading = true;
        new Thread(() -> {
            try {
                try {
                    JSONObject info = api.myInfo();
                    JSONObject d = info.optJSONObject("data");
                    if (d != null) myUserId = d.optInt("id", 0);
                } catch (Exception ignored) {}
                final JSONObject d = api.detail(id);
                runOnUiThread(() -> { loading = false; detail = d; render(); });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loading = false;
                    stopCountdown();
                    content.removeAllViews();
                    TextView t = new TextView(this);
                    t.setText("加载详情失败：" + e.getMessage());
                    t.setTextColor(cSub()); t.setPadding(dp(4), dp(24), 0, 0);
                    content.addView(t);
                });
            }
        }).start();
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(bg(cCard(), 16, 0, 0));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setElevation(dp(3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView line(String label, String value) {
        TextView t = new TextView(this);
        t.setTextSize(13);
        t.setTextColor(cText());
        t.setPadding(0, dp(3), 0, dp(3));
        t.setText(label + value);
        return t;
    }

    private String namesOf(JSONArray arr) {
        if (arr == null || arr.length() == 0) return "不限";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            String n = o != null ? o.optString("name") : arr.optString(i);
            if (n != null && !n.isEmpty()) { if (sb.length() > 0) sb.append("、"); sb.append(n); }
        }
        return sb.length() == 0 ? "不限" : sb.toString();
    }

    private String genderText(int g) { return g == 1 ? "男" : (g == 2 ? "女" : "不限"); }

    private void render() {
        stopCountdown();
        content.removeAllViews();
        if (detail == null) return;
        int status = detail.optInt("status");
        int applyStatus = detail.optInt("applyStatus");
        topEnroll.setVisibility(applyStatus == 2 ? View.GONE : View.VISIBLE);

        LinearLayout head = card();
        TextView nm = new TextView(this);
        nm.setText(detail.optString("name", name));
        nm.setTextSize(18); nm.setTextColor(cText()); nm.setTypeface(null, Typeface.BOLD);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        nm.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        favBtn = new TextView(this);
        favBtn.setTextSize(13);
        favBtn.setPadding(dp(10), dp(5), dp(10), dp(5));
        favBtn.setOnClickListener(v -> toggleFavorite());
        titleRow.addView(nm); titleRow.addView(favBtn);
        head.addView(titleRow);
        updateFavButton(Favorites.refreshIfSaved(this, detail, id));
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(8), 0, dp(4));
        chips.addView(chip(stName(status), 0xFFFFFFFF, stColor(status)));
        if (!detail.optString("classifyName").isEmpty()) chips.addView(chip(detail.optString("classifyName"), cAccent(), 0x1A000000 | (accent & 0xFFFFFF)));
        head.addView(chips);
        countdownText = new TextView(this);
        countdownText.setTextSize(15);
        countdownText.setTypeface(null, Typeface.BOLD);
        countdownText.setTextColor(cAccent());
        countdownText.setPadding(dp(12), dp(9), dp(12), dp(9));
        countdownText.setBackground(bg(0x1A000000 | (accent & 0xFFFFFF), 12, 0, 0));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(8);
        countdownText.setLayoutParams(clp);
        countdownText.setVisibility(View.GONE);
        head.addView(countdownText);
        head.addView(line("举办单位：", detail.optString("organizationName")));
        content.addView(head);

        LinearLayout info = card();
        info.addView(line("学时：", detail.optString("hours")));
        info.addView(line("报名时间：", fmt(detail.optLong("enrollStartTime")) + " ~ " + fmt(detail.optLong("enrollEndTime"))));
        info.addView(line("活动时间：", fmt(detail.optLong("startTime")) + " ~ " + fmt(detail.optLong("endTime"))));
        info.addView(line("地点：", detail.optString("address")));
        info.addView(line("学院限制：", namesOf(detail.optJSONArray("collegeList"))));
        info.addView(line("年级限制：", namesOf(detail.optJSONArray("gradeList"))));
        info.addView(line("性别限制：", genderText(detail.optInt("genderLimit"))));
        int honest = detail.optInt("honestLimit", 0);
        if (honest > 0) info.addView(line("诚信分要求：", "≥ " + honest));
        if (!detail.optString("unionOrganizer").isEmpty()) info.addView(line("联合主办：", detail.optString("unionOrganizer")));
        JSONObject mgr = detail.optJSONObject("manager");
        String contact = detail.optString("contact");
        info.addView(line("联系人：", (mgr != null ? mgr.optString("name") : "") + (contact.isEmpty() ? "" : "（" + contact + "）")));
        int max = detail.optInt("peopleLimit"), cnt = detail.optInt("joinMemberCount");
        if (max > 0) {
            TextView q = new TextView(this);
            q.setTextSize(12); q.setTextColor(cSub()); q.setPadding(0, dp(4), 0, 0);
            q.setText("名额：已报 " + cnt + " / 上限 " + max);
            info.addView(q);
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(max); pb.setProgress(Math.max(0, Math.min(cnt, max)));
            pb.setProgressTintList(android.content.res.ColorStateList.valueOf(cAccent()));
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
            plp.topMargin = dp(6); pb.setLayoutParams(plp);
            info.addView(pb);
        }
        content.addView(info);

        LinearLayout cal = card();
        TextView calTitle = new TextView(this);
        calTitle.setText("添加到系统日历");
        calTitle.setTextSize(14); calTitle.setTextColor(cText()); calTitle.setTypeface(null, Typeface.BOLD);
        cal.addView(calTitle);
        TextView calHint = new TextView(this);
        calHint.setText("抢活动日程用报名时间，参加活动日程用活动时间");
        calHint.setTextSize(11); calHint.setTextColor(cSub()); calHint.setPadding(0, dp(4), 0, dp(8));
        cal.addView(calHint);
        LinearLayout calBtns = new LinearLayout(this);
        calBtns.setOrientation(LinearLayout.HORIZONTAL);
        final String actName = detail.optString("name", name);
        Button grabCal = btn("抢活动日程", false);
        grabCal.setOnClickListener(v -> addCalendarEvent("抢PU\"" + actName + "\"",
                detail.optLong("enrollStartTime"), detail.optLong("enrollEndTime"), false));
        Button joinCal = btn("参加活动日程", true);
        joinCal.setOnClickListener(v -> addCalendarEvent("参加\"" + actName + "\"",
                detail.optLong("startTime"), detail.optLong("endTime"), true));
        calBtns.addView(grabCal); calBtns.addView(joinCal);
        cal.addView(calBtns);
        content.addView(cal);

        String intro = detail.optString("introduce");
        if (!intro.isEmpty()) {
            LinearLayout ic = card();
            TextView ih = new TextView(this);
            ih.setText("活动简介"); ih.setTextSize(14); ih.setTextColor(cText()); ih.setTypeface(null, Typeface.BOLD);
            ic.addView(ih);
            TextView it = new TextView(this);
            it.setText(intro); it.setTextSize(13); it.setTextColor(cSub()); it.setPadding(0, dp(6), 0, 0);
            ic.addView(it);
            content.addView(ic);
        }

        JSONArray att = detail.optJSONArray("attachment");
        if (att != null && att.length() > 0) {
            LinearLayout ac = card();
            TextView ah = new TextView(this);
            ah.setText("附件（" + att.length() + "）"); ah.setTextSize(14); ah.setTextColor(cText()); ah.setTypeface(null, Typeface.BOLD);
            ac.addView(ah);
            for (int i = 0; i < att.length(); i++) {
                final JSONObject a = att.optJSONObject(i);
                if (a == null) continue;
                TextView row = line("· ", a.optString("name"));
                row.setTextColor(cAccent());
                row.setPadding(0, dp(6), 0, dp(6));
                row.setOnClickListener(v -> showAttachDialog(a));
                ac.addView(row);
            }
            content.addView(ac);
        }

        // 操作区（立即抢已放到顶栏；这里只保留已报名的操作）
        if (applyStatus == 2) {
            LinearLayout act = card();
            LinearLayout row1 = new LinearLayout(this);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            row1.setPadding(0, dp(4), 0, 0);
            Button sign = btn("出示签到码", true); sign.setOnClickListener(v -> showSignQr());
            Button rec = btn("打卡记录", false); rec.setOnClickListener(v -> showRecords());
            row1.addView(sign); row1.addView(rec);
            if (status == 5) {   // 仅报名阶段(status=5 报名中)可取消
                Button cancel = btn("取消报名", false); cancel.setOnClickListener(v -> cancelEnroll());
                row1.addView(cancel);
            } else {
                TextView nc = new TextView(this);
                nc.setText("报名阶段已结束，不可取消报名");
                nc.setTextSize(11); nc.setTextColor(cSub()); nc.setPadding(dp(8), dp(8), 0, 0);
                row1.addView(nc);
            }
            act.addView(row1);
            content.addView(act);
        }
        startCountdown();
    }

    private void toggleFavorite() {
        if (detail == null) return;
        boolean saved = Favorites.toggle(this, detail, id);
        updateFavButton(saved);
        toast(saved ? "已收藏，可在「我的」里查看" : "已取消收藏");
    }

    private void updateFavButton(boolean saved) {
        if (favBtn == null) return;
        favBtn.setText(saved ? "★ 已收藏" : "☆ 收藏");
        favBtn.setTextColor(saved ? 0xFFE6A23C : cAccent());
        favBtn.setBackground(bg(saved ? 0x1AE6A23C : (0x1A000000 | (accent & 0xFFFFFF)), 14, 0, 0));
    }

    /** 调系统日历的插入界面创建日程，由用户在日历应用中确认保存。 */
    private void addCalendarEvent(String title, long begin, long end, boolean withLocation) {
        if (begin <= 0 || end <= 0 || end <= begin) {
            toast("该活动时间信息不完整，无法创建日程");
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_INSERT)
                    .setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
                    .putExtra(CalendarContract.Events.TITLE, title)
                    .putExtra(CalendarContract.Events.DESCRIPTION, "来自二课助手");
            if (withLocation) i.putExtra(CalendarContract.Events.EVENT_LOCATION, detail.optString("address"));
            startActivity(i);
        } catch (Exception e) {
            toast("未找到可用的日历应用");
        }
    }

    private void startCountdown() {
        stopCountdown();
        countdownTick = new Runnable() {
            @Override
            public void run() {
                if (!tickCountdown()) return;
                countdownHandler.postDelayed(this, 1000);
            }
        };
        countdownHandler.post(countdownTick);
    }

    private void stopCountdown() {
        if (countdownTick != null) {
            countdownHandler.removeCallbacks(countdownTick);
            countdownTick = null;
        }
    }

    /** 用系统时间与报名开始/截止时间做减法；返回是否继续每秒刷新。 */
    private boolean tickCountdown() {
        if (detail == null || countdownText == null) return false;
        long now = System.currentTimeMillis();
        long start = detail.optLong("enrollStartTime");
        long end = detail.optLong("enrollEndTime");
        long target;
        String label;
        if (start > 0 && now < start) {
            target = start; label = "报名开始倒计时";
        } else if (end > 0 && now < end) {
            target = end; label = "报名截止倒计时";
        } else if (start <= 0 && end <= 0) {
            countdownText.setVisibility(View.GONE);
            return false;
        } else {
            countdownText.setVisibility(View.VISIBLE);
            countdownText.setTextColor(cSub());
            countdownText.setText(detail.optInt("status") == 8 ? "报名已结束，活动已结束" : "报名已结束");
            return false;
        }
        countdownText.setVisibility(View.VISIBLE);
        countdownText.setTextColor(cAccent());
        countdownText.setText(label + "  " + fmtLeft(target - now));
        return true;
    }

    private static String fmtLeft(long ms) {
        long s = Math.max(0, ms / 1000);
        long d = s / 86400; s %= 86400;
        long h = s / 3600; s %= 3600;
        long m = s / 60; s %= 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("天 ");
        sb.append(String.format(Locale.US, "%02d:%02d:%02d", h, m, s));
        return sb.toString();
    }

    @Override
    protected void onDestroy() {
        stopCountdown();
        super.onDestroy();
    }

    private TextView chip(String text, int fg, int bgColor) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(11); t.setTextColor(fg);
        t.setPadding(dp(8), dp(2), dp(8), dp(2));
        GradientDrawable g = new GradientDrawable();
        g.setColor(bgColor); g.setCornerRadius(dp(20));
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8); t.setLayoutParams(lp);
        return t;
    }

    private Button btn(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text); b.setTextSize(12); b.setAllCaps(false);
        if (primary) { b.setTextColor(0xFFFFFFFF); b.setBackground(bg(cAccent(), 22, 0, 0)); }
        else { b.setTextColor(cAccent()); b.setBackground(bg(0x1A000000 | (accent & 0xFFFFFF), 22, 1, 0x55000000 | (accent & 0xFFFFFF))); }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        lp.rightMargin = dp(8); b.setLayoutParams(lp);
        return b;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void manualEnroll() {
        new AlertDialog.Builder(this).setTitle("立即报名")
                .setMessage("确定立即报名该活动？")
                .setPositiveButton("立即报名", (d, w) -> new Thread(() -> {
                    try {
                        JSONObject r = api.enroll(id, new JSONArray());
                        int code = r.optInt("code"); String msg = r.optString("msg");
                        boolean ok = code == 0 || msg.contains("已报") || msg.contains("重复");
                        final String out = ok ? "报名成功" : ("报名失败：" + msg + "（code=" + code + "）");
                        runOnUiThread(() -> { toast(out); Notify.post(ActivityDetailActivity.this, ok ? "✅ 报名成功" : "报名未成功", out, id, true); load(); });
                    } catch (Exception e) { runOnUiThread(() -> toast("报名失败：" + e.getMessage())); }
                }).start())
                .setNegativeButton("取消", null).show();
    }

    private void cancelEnroll() {
        final EditText input = new EditText(this);
        input.setHint("取消原因（可留空）");
        new AlertDialog.Builder(this).setTitle("取消报名").setView(input)
                .setPositiveButton("确定取消", (d, w) -> new Thread(() -> {
                    try {
                        JSONObject r = api.cancel(id, input.getText().toString().trim());
                        int code = r.optInt("code"); String msg = r.optString("msg");
                        final String out = code == 0 ? "已取消报名" : ("取消失败：" + msg + "（code=" + code + "）");
                        runOnUiThread(() -> { toast(out); Notify.post(ActivityDetailActivity.this, "报名状态更新", out, id, true); load(); });
                    } catch (Exception e) { runOnUiThread(() -> toast("取消失败：" + e.getMessage())); }
                }).start())
                .setNegativeButton("再想想", null).show();
    }

    private void showRecords() {
        new Thread(() -> {
            try {
                JSONArray arr = api.signRecords(myUserId, id);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject r = arr.optJSONObject(i);
                    if (r == null) continue;
                    sb.append("• ").append(r.optString("signTime", r.optString("createTime", r.optString("time", "")))).append("\n");
                }
                final String s = sb.length() == 0 ? "暂无签到/签退记录" : sb.toString();
                runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("打卡记录").setMessage(s).setPositiveButton("关闭", null).show());
            } catch (Exception e) { runOnUiThread(() -> toast("打卡记录获取失败：" + e.getMessage())); }
        }).start();
    }

    private void showSignQr() {
        if (myUserId == 0) { toast("正在获取账号信息，请稍后再试"); load(); return; }
        SignQrDialog.show(this, api, id, myUserId, this::load);
    }

    private void showAttachDialog(JSONObject a) {
        final String url = a.optString("url");
        final String nm = a.optString("name", "附件");
        String[] opts = {"预览 / 打开（不保存）", "下载到本机", "取消"};
        new AlertDialog.Builder(this).setTitle(nm).setItems(opts, (d, w) -> {
            if (w == 0) previewAttach(url, nm);
            else if (w == 1) downloadAttach(url, nm);
        }).show();
    }

    private String absUrl(String u) {
        if (u == null) return "";
        if (u.startsWith("http")) return u;
        if (u.startsWith("/")) return "https://ekta.sdust.edu.cn" + u;
        return "https://ekta.sdust.edu.cn/" + u;
    }

    private static String extOf(String name) {
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1).toLowerCase() : "";
    }

    private static String guessMime(String name) {
        String e = extOf(name);
        switch (e) {
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "pdf": return "application/pdf";
            case "doc": return "application/msword";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls": return "application/vnd.ms-excel";
            case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt": return "application/vnd.ms-powerpoint";
            case "pptx": return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "zip": return "application/zip";
            case "txt": return "text/plain";
            default: {
                String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(e);
                return m == null ? "application/octet-stream" : m;
            }
        }
    }

    private File downloadToCache(String url, String nm) throws Exception {
        String u = absUrl(url);
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        String tk = sp.getString("token", "");
        if (!tk.isEmpty()) c.setRequestProperty("Authorization", tk);
        File dir = new File(getCacheDir(), "attach");
        if (!dir.exists()) dir.mkdirs();
        String safe = nm.replaceAll("[\\\\/:*?\"<>|]", "_");
        File f = new File(dir, safe);
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return f;
    }

    private void previewAttach(final String url, final String nm) {
        toast("加载中…");
        new Thread(() -> {
            try {
                final File f = downloadToCache(url, nm);
                final String mime = guessMime(nm);
                if (mime.startsWith("image/")) {
                    final Bitmap bm = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
                    runOnUiThread(() -> {
                        if (bm == null) { toast("图片解析失败"); return; }
                        ImageView iv = new ImageView(this);
                        iv.setAdjustViewBounds(true);
                        iv.setImageBitmap(bm);
                        new AlertDialog.Builder(this).setTitle(nm).setView(iv).setPositiveButton("关闭", null).show();
                    });
                } else {
                    runOnUiThread(() -> openFile(f, mime, url));
                }
            } catch (Exception e) {
                runOnUiThread(() -> toast("预览失败：" + e.getMessage()));
            }
        }).start();
    }

    private void openFile(File f, String mime, String url) {
        try {
            Uri u = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(u, mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(Intent.createChooser(i, "选择打开方式"));
        } catch (Exception e) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(absUrl(url)))); }
            catch (Exception e2) { toast("无法打开：" + e.getMessage()); }
        }
    }

    private void downloadAttach(String url, String nm) {
        try {
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(absUrl(url)));
            String safe = nm.replaceAll("[\\\\/:*?\"<>|]", "_");
            req.setTitle(nm);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safe);
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(req);
            toast("已开始下载到“下载”目录");
        } catch (Exception e) {
            toast("下载失败：" + e.getMessage());
        }
    }

    private Bitmap qrBitmap(String content, int size) {
        try {
            com.google.zxing.qrcode.QRCodeWriter w = new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix m = w.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size);
            int[] px = new int[size * size];
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) px[y * size + x] = m.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            bmp.setPixels(px, 0, size, 0, 0, size, size);
            return bmp;
        } catch (Exception e) { return null; }
    }
}
