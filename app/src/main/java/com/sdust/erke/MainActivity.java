package com.sdust.erke;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {
    private SharedPreferences sp;
    private Api api = new Api();
    private TextView tvUser, tvStatus, tvBell;
    private LinearLayout actBox, myBox;
    private FrameLayout contentFrame;
    private ScrollView[] pages = new ScrollView[2];
    private TextView[] tabs = new TextView[2];
    private int curTab = 0;

    private boolean dark = false;
    private int accent = 0xFF0B8F6A;

    private int myUserId = 0;
    private int pendingSignActivity = 0;
    private JSONArray myListCache = new JSONArray();
    private static final int REQ_VPN_RELOGIN = 201;
    private boolean vpnDialogShown = false;
    private JSONArray allActivities = new JSONArray();
    private String fKeyword = "";
    private final java.util.LinkedHashSet<String> fClass = new java.util.LinkedHashSet<>();
    private final java.util.LinkedHashSet<Integer> fStatus = new java.util.LinkedHashSet<>();
    private final java.util.LinkedHashSet<String> fOrg = new java.util.LinkedHashSet<>();
    private int fSort = 0;
    private TextView actCount;
    private Button btnClass, btnOrg, btnSort, btnTime;
    private TextView[] statusChips;
    private int fTime = 0;
    private EditText actSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("erke", MODE_PRIVATE);
        dark = sp.getBoolean("dark", false);
        accent = sp.getInt("accent", 0xFF0B8F6A);
        String tk = sp.getString("token", "");
        if (tk.isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        api.setToken(tk);
        api.setVpn(sp.getBoolean("use_vpn", false), sp.getString("vpn_cookie", ""));
        buildUi();
        // 冷启动时若 webVPN 会话快过期，自动静默续期一次（避免用到一半被 403）
        if (Vpn.needsRefresh(sp) && Vpn.hasCreds(sp)) {
            startActivityForResult(new Intent(this, VpnLoginActivity.class)
                    .putExtra(VpnLoginActivity.EXTRA_SILENT, true), REQ_VPN_RELOGIN);
        }
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        maybePromptKeepAlive();
    }

    @Override protected void onResume() {
        super.onResume();
        api.setVpn(sp.getBoolean("use_vpn", false), sp.getString("vpn_cookie", ""));
        updateBell();
    }

    /** 顶栏“消息”未读角标（消息历史持久化在 Notify 的独立存储里）。 */
    private void updateBell() {
        if (tvBell == null) return;
        try {
            int n = Notify.unread(this);
            tvBell.setText(n > 0 ? ("消息 " + (n > 99 ? "99+" : n)) : "消息");
            tvBell.setTextColor(n > 0 ? 0xFFE53935 : cAccent());
        } catch (Throwable ignored) {}
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    // ---------------- 主题色 ----------------
    private int cBg() { return dark ? 0xFF121417 : 0xFFF2F4F7; }
    private int cCard() { return dark ? 0xFF1E2228 : 0xFFFFFFFF; }
    private int cText() { return dark ? 0xFFECECEC : 0xFF222222; }
    private int cSub() { return dark ? 0xFF9AA0A6 : 0xFF777777; }
    private int cAccent() { return accent; }
    private int accentAlpha(int a) { return (a << 24) | (accent & 0x00FFFFFF); }

    // ---------------- 布局 ----------------
    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg());
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(12), dp(16), dp(10));
        top.setBackgroundColor(cCard());
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView app = new TextView(this);
        app.setText("二课助手");
        app.setTextSize(18); app.setTextColor(cAccent()); app.setTypeface(null, Typeface.BOLD);
        tvUser = new TextView(this);
        tvUser.setTextSize(11); tvUser.setTextColor(cSub()); tvUser.setText(sp.getString("user", ""));
        titles.addView(app); titles.addView(tvUser);
        tvBell = new TextView(this);
        tvBell.setText("消息"); tvBell.setTextSize(13); tvBell.setTextColor(cAccent());
        tvBell.setPadding(dp(8), dp(6), dp(10), dp(6));
        tvBell.setOnClickListener(v -> startActivity(new Intent(this, NotificationActivity.class)));
        TextView settings = new TextView(this);
        settings.setText("设置"); settings.setTextSize(13); settings.setTextColor(cAccent());
        settings.setPadding(dp(8), dp(6), dp(10), dp(6));
        settings.setOnClickListener(v -> showSettings());
        TextView logout = new TextView(this);
        logout.setText("退出"); logout.setTextSize(13); logout.setTextColor(cAccent());
        logout.setPadding(dp(8), dp(6), 0, dp(6));
        logout.setOnClickListener(v -> doLogout());
        top.addView(titles); top.addView(tvBell); top.addView(settings); top.addView(logout);
        root.addView(top);
        updateBell();

        tvStatus = new TextView(this);
        tvStatus.setTextSize(12); tvStatus.setTextColor(cSub());
        tvStatus.setPadding(dp(16), dp(6), dp(16), dp(6));
        root.addView(tvStatus);

        contentFrame = new FrameLayout(this);
        contentFrame.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(contentFrame);

        // 页1：我的活动
        LinearLayout myPage = new LinearLayout(this);
        myPage.setOrientation(LinearLayout.VERTICAL);
        myPage.setPadding(dp(14), dp(10), dp(14), dp(10));
        myPage.addView(hRow("我的活动", v -> refreshMy()));
        myBox = new LinearLayout(this);
        myBox.setOrientation(LinearLayout.VERTICAL);
        myPage.addView(myBox);
        pages[0] = wrapScroll(myPage); contentFrame.addView(pages[0]);

        // 页2：活动广场
        LinearLayout actPage = new LinearLayout(this);
        actPage.setOrientation(LinearLayout.VERTICAL);
        actPage.setPadding(dp(14), dp(10), dp(14), dp(10));
        actPage.addView(hRow("活动广场", v -> refreshActivities(true)));
        actPage.addView(buildFilterBar());
        actCount = new TextView(this);
        actCount.setTextSize(12); actCount.setTextColor(cSub());
        actCount.setPadding(dp(2), 0, 0, dp(6));
        actPage.addView(actCount);
        actBox = new LinearLayout(this);
        actBox.setOrientation(LinearLayout.VERTICAL);
        actPage.addView(actBox);
        pages[1] = wrapScroll(actPage); contentFrame.addView(pages[1]);

        // 底部导航
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(cCard());
        nav.setPadding(0, dp(4), 0, dp(4));
        nav.setElevation(dp(8));
        String[] names = {"我的活动", "活动广场"};
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            TextView tab = new TextView(this);
            tab.setText(names[i]);
            tab.setTextSize(13);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0, dp(10), 0, dp(10));
            tab.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            tab.setOnClickListener(v -> setTab(idx));
            tabs[i] = tab;
            nav.addView(tab);
        }
        root.addView(nav);

        setTab(0);
    }

    private ScrollView wrapScroll(View content) {
        ScrollView s = new ScrollView(this);
        s.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        s.addView(content);
        return s;
    }

    private LinearLayout hRow(String title, View.OnClickListener refresh) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, dp(10));
        TextView t = new TextView(this);
        t.setText(title); t.setTextSize(16); t.setTextColor(cText()); t.setTypeface(null, Typeface.BOLD);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button b = btn("刷新", false);
        b.setOnClickListener(refresh);
        row.addView(t); row.addView(b);
        return row;
    }

    private void setTab(int i) {
        curTab = i;
        for (int k = 0; k < 2; k++) {
            if (pages[k] != null) pages[k].setVisibility(k == i ? View.VISIBLE : View.GONE);
            if (tabs[k] != null) {
                tabs[k].setTextColor(k == i ? cAccent() : cSub());
                tabs[k].setTypeface(null, k == i ? Typeface.BOLD : Typeface.NORMAL);
            }
        }
        if (i == 0) refreshMy();
        else if (i == 1) refreshActivities(false);
    }

    private GradientDrawable bg(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(bg(cCard(), 16, 0, 0));
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        c.setElevation(dp(3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView chip(String text, int fg, int bgColor) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(11); t.setTextColor(fg);
        t.setPadding(dp(8), dp(2), dp(8), dp(2));
        t.setBackground(bg(bgColor, 20, 0, 0));
        return t;
    }

    private Button btn(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text); b.setTextSize(12); b.setAllCaps(false);
        if (primary) { b.setTextColor(0xFFFFFFFF); b.setBackground(bg(cAccent(), 22, 0, 0)); }
        else { b.setTextColor(cAccent()); b.setBackground(bg(accentAlpha(0x14), 22, 1, accentAlpha(0x33))); }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        lp.rightMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private int statusColor(int st) {
        switch (st) {
            case 4: return 0xFFE6A23C;
            case 5: return 0xFF0B8F6A;
            case 6: return 0xFF409EFF;
            case 7: return 0xFF909399;
            default: return 0xFF909399;
        }
    }

    private TextView emptyText(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(13); t.setTextColor(cSub());
        t.setPadding(dp(4), dp(24), dp(4), dp(24));
        return t;
    }

    private TextView sectionText(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(13); t.setTextColor(cSub()); t.setTypeface(null, Typeface.BOLD);
        t.setPadding(dp(2), dp(8), dp(2), dp(6));
        return t;
    }

    private void addQuota(LinearLayout card, int count, int max) {
        if (max <= 0) return;
        TextView label = new TextView(this);
        label.setTextSize(11); label.setTextColor(cSub());
        label.setText("已报 " + count + " / 上限 " + max);
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(max); pb.setProgress(Math.max(0, Math.min(count, max)));
        pb.setProgressTintList(ColorStateList.valueOf(cAccent()));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        plp.topMargin = dp(6);
        pb.setLayoutParams(plp);
        card.addView(label); card.addView(pb);
    }

    // ---------------- 数据刷新 ----------------
    private void runBg(Runnable r) { new Thread(r).start(); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void refreshMy() {
        runBg(() -> {
            try {
                if (myUserId == 0) {
                    JSONObject info = api.myInfo();
                    JSONObject d = info.optJSONObject("data");
                    if (d != null) myUserId = d.optInt("id", 0);
                }
                JSONObject data = api.myList();
                JSONArray list = data.optJSONArray("list");
                if (list == null) list = new JSONArray();
                final JSONArray fl = list;
                runOnUiThread(() -> { tvStatus.setText("我的活动：" + fl.length() + " 个"); renderMyList(fl); });
            } catch (Exception e) {
                final String m = e.getMessage();
                runOnUiThread(() -> {
                    if (isBlocked(m)) { tvStatus.setText("我的活动：网络受限"); promptVpnRelogin(); }
                    else tvStatus.setText("我的活动刷新失败：" + m);
                });
            }
        });
    }

    private void refreshActivities(boolean ignored) {
        runBg(() -> {
            try {
                final JSONArray arr = api.listActivities("4,5,6,7");
                runOnUiThread(() -> {
                    allActivities = arr;
                    tvStatus.setText("活动数：" + arr.length());
                    updateFilterLabels();
                    renderActivities();
                });
            } catch (Exception e) {
                final String m = e.getMessage();
                runOnUiThread(() -> {
                    if (isBlocked(m)) { tvStatus.setText("活动广场：网络受限"); promptVpnRelogin(); }
                    else tvStatus.setText("刷新失败：" + m);
                });
            }
        });
    }

    private boolean isBlocked(String m) {
        if (m == null) return false;
        return m.contains("403") || m.contains("访问策略") || m.contains("返回网页")
                || m.contains("非JSON") || m.contains("<!DOCTYPE");
    }

    private void promptVpnRelogin() {
        if (vpnDialogShown) return;
        vpnDialogShown = true;
        new AlertDialog.Builder(this)
                .setTitle("网络受限 / webVPN 可能失效")
                .setMessage("访问被拒绝，可能是 webVPN 会话过期。是否重新登录 webVPN？")
                .setPositiveButton("重新登录", (d, w) -> {
                    vpnDialogShown = false;
                    startActivityForResult(new Intent(this, VpnLoginActivity.class), REQ_VPN_RELOGIN);
                })
                .setNegativeButton("取消", (d, w) -> vpnDialogShown = false)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN_RELOGIN) {
            if (resultCode == RESULT_OK) {
                api.setVpn(true, sp.getString("vpn_cookie", ""));
                toast("webVPN 已刷新，正在重试…");
                setTab(curTab);
            } else {
                toast("webVPN 未刷新（可稍后在设置里重试）");
            }
        }
    }

    private static String statusName(int st) {
        switch (st) {
            case 1: return "草稿"; case 2: return "待审核"; case 3: return "审核通过";
            case 4: return "报名未开始"; case 5: return "报名中"; case 6: return "进行中";
            case 7: return "已结束"; case 8: return "已取消"; default: return "状态" + st;
        }
    }

    private static String fmt(long ms) {
        if (ms <= 0) return "-";
        return new SimpleDateFormat("MM-dd HH:mm").format(new Date(ms));
    }

    // ---------------- 我的活动 ----------------
    private void renderMyList(JSONArray arr) {
        myListCache = arr;
        myBox.removeAllViews();
        if (arr.length() == 0) { myBox.addView(emptyText("暂无已报名活动，去“活动广场”报名后再刷新")); return; }
        int[] order = {5, 6, 7, 4, 8, 3, 2, 1};
        boolean any = false;
        for (int g : order) {
            boolean header = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null || o.optInt("status") != g) continue;
                if (!header) { myBox.addView(sectionText(statusName(g))); header = true; any = true; }
                addMyCard(o);
            }
        }
        if (!any) for (int i = 0; i < arr.length(); i++) { JSONObject o = arr.optJSONObject(i); if (o != null) addMyCard(o); }
    }

    private void addMyCard(JSONObject o) {
        final int id = o.optInt("id");
        final int st = o.optInt("status");
        LinearLayout c = card();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText(o.optString("name"));
        name.setTextSize(15); name.setTextColor(cText()); name.setTypeface(null, Typeface.BOLD);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(name); head.addView(chip(statusName(st), 0xFFFFFFFF, statusColor(st)));
        c.addView(head);
        int sw = o.optInt("signSwitch", 0);
        String phase = sw == 1 ? "签到阶段" : sw == 2 ? "签退阶段" : "未开始/无需";
        TextView meta = new TextView(this);
        meta.setTextSize(12); meta.setTextColor(cSub()); meta.setPadding(0, dp(6), 0, 0);
        meta.setText("种类：" + o.optString("classifyName")
                + "\n时间：" + fmt(o.optLong("startTime")) + " ~ " + fmt(o.optLong("endTime"))
                + "\n地点：" + o.optString("address") + "　打卡：" + phase + "　学时：" + o.optString("hours"));
        c.addView(meta);
        c.setOnClickListener(v -> openDetail(id, o.optString("name")));
        addQuota(c, 0, 0); // 名额下面异步补
        final LinearLayout cardRef = c;
        runBg(() -> {
            try {
                JSONObject d = api.detail(id);
                final int cnt = d.optInt("joinMemberCount"), max = d.optInt("peopleLimit");
                if (max > 0) runOnUiThread(() -> addQuota(cardRef, cnt, max));
            } catch (Exception ignored) {}
        });
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL); btns.setPadding(0, dp(10), 0, 0);
        Button sign = btn("出示签到码", true); sign.setOnClickListener(v -> showSignQr(id));
        Button rec = btn("打卡记录", false); rec.setOnClickListener(v -> showRecords(id));
        Button stt = btn("查状态", false); stt.setOnClickListener(v -> queryOne(id));
        Button cancel = btn("取消报名", false); cancel.setOnClickListener(v -> cancelEnroll(id));
        btns.addView(sign); btns.addView(rec); btns.addView(stt); btns.addView(cancel);
        c.addView(btns);
        myBox.addView(c);
    }

    // ---------------- 活动广场 ----------------
    private void renderActivities() {
        actBox.removeAllViews();
        java.util.List<JSONObject> items = filteredActivities();
        if (actCount != null) actCount.setText("筛选结果 " + items.size() + " / 全部 " + allActivities.length());
        if (items.isEmpty()) { actBox.addView(emptyText("没有符合条件的活动，试试调整筛选")); return; }
        for (JSONObject o : items) addActivityCard(o);
    }

    private void addActivityCard(JSONObject o) {
        int st = o.optInt("activityStatus");
        LinearLayout c = card();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText(o.optString("name"));
        name.setTextSize(15); name.setTextColor(cText()); name.setTypeface(null, Typeface.BOLD);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(name); head.addView(chip(statusName(st), 0xFFFFFFFF, statusColor(st)));
        c.addView(head);
        TextView meta = new TextView(this);
        meta.setTextSize(12); meta.setTextColor(cSub()); meta.setPadding(0, dp(6), 0, 0);
        meta.setText("种类：" + o.optString("classifyName") + "　主办：" + o.optString("organizationName")
                + "\n学时：" + o.optString("hours")
                + "\n报名：" + fmt(o.optLong("enrollStartTime")) + " ~ " + fmt(o.optLong("enrollEndTime"))
                + "\n活动：" + fmt(o.optLong("startTime")) + " ~ " + fmt(o.optLong("endTime")));
        c.addView(meta);
        c.setOnClickListener(v -> openDetail(o.optInt("id"), o.optString("name")));
        addQuota(c, o.optInt("enrollCount"), o.optInt("peopleLimit"));
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL); btns.setPadding(0, dp(10), 0, 0);
        Button grab = btn("立即抢", true); grab.setOnClickListener(v -> manualEnroll(o.optInt("id"), o.optString("name")));
        btns.addView(grab);
        c.addView(btns);
        actBox.addView(c);
    }

    private LinearLayout buildFilterBar() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, 0, 0, dp(6));
        actSearch = new EditText(this);
        actSearch.setHint("搜索活动名称 / 种类…");
        actSearch.setSingleLine(true);
        actSearch.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { fKeyword = s.toString().trim(); renderActivities(); }
            public void afterTextChanged(android.text.Editable s) {}
        });
        wrap.addView(actSearch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        btnClass = filterBtn("种类", v -> showClassFilter());
        btnOrg = filterBtn("学院", v -> showOrgFilter());
        btnSort = filterBtn("排序", v -> showSortFilter());
        btnTime = filterBtn("时间", v -> showTimeFilter());
        Button reset = filterBtn("重置", v -> resetFilters());
        row.addView(btnClass); row.addView(btnOrg); row.addView(btnSort); row.addView(btnTime); row.addView(reset);
        hs.addView(row); wrap.addView(hs);

        // 状态：可单/多选的开关 chips
        HorizontalScrollView hs2 = new HorizontalScrollView(this);
        hs2.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(8), 0, 0);
        final int[] vals = {4, 5, 6, 7};
        final String[] labs = {"报名未开始", "报名中", "进行中", "已结束"};
        statusChips = new TextView[vals.length];
        for (int i = 0; i < vals.length; i++) {
            final int v = vals[i];
            TextView ch = new TextView(this);
            ch.setText(labs[i]);
            ch.setTextSize(11);
            ch.setPadding(dp(10), dp(4), dp(10), dp(4));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            ch.setLayoutParams(lp);
            ch.setOnClickListener(x -> toggleStatus(v));
            statusChips[i] = ch;
            chips.addView(ch);
        }
        hs2.addView(chips);
        wrap.addView(hs2);
        updateStatusChips();
        return wrap;
    }

    private void toggleStatus(int v) {
        if (fStatus.contains(v)) fStatus.remove(v); else fStatus.add(v);
        updateStatusChips();
        renderActivities();
    }

    private void updateStatusChips() {
        if (statusChips == null) return;
        int[] vals = {4, 5, 6, 7};
        for (int i = 0; i < statusChips.length; i++) {
            boolean on = fStatus.contains(vals[i]);
            statusChips[i].setTextColor(on ? 0xFFFFFFFF : cAccent());
            statusChips[i].setBackground(bg(on ? cAccent() : accentAlpha(0x10), 20, 1, accentAlpha(0x40)));
        }
    }

    private void showTimeFilter() {
        final String[] opts = {"全部时间", "今天", "本周", "本月"};
        new AlertDialog.Builder(this).setTitle("时间范围（按活动开始时间）")
                .setSingleChoiceItems(opts, fTime, (d, w) -> { fTime = w; d.dismiss(); updateFilterLabels(); renderActivities(); })
                .show();
    }

    private Button filterBtn(String t, View.OnClickListener l) {
        Button b = btn(t, false);
        b.setTextSize(11);
        b.setOnClickListener(l);
        return b;
    }

    private java.util.List<JSONObject> filteredActivities() {
        java.util.List<JSONObject> out = new ArrayList<>();
        String kw = fKeyword.toLowerCase();
        for (int i = 0; i < allActivities.length(); i++) {
            JSONObject o = allActivities.optJSONObject(i);
            if (o == null) continue;
            String nm = (o.optString("name") + " " + o.optString("classifyName")).toLowerCase();
            if (!kw.isEmpty() && !nm.contains(kw)) continue;
            if (!fClass.isEmpty() && !fClass.contains(o.optString("classifyName"))) continue;
            if (!fStatus.isEmpty() && !fStatus.contains(o.optInt("activityStatus"))) continue;
            if (!fOrg.isEmpty() && !fOrg.contains(o.optString("organizationName"))) continue;
            if (fTime > 0) {
                long[] rg = timeRange(fTime);
                long st = o.optLong("startTime");
                if (st < rg[0] || st > rg[1]) continue;
            }
            out.add(o);
        }
        if (fSort != 0) {
            java.util.Collections.sort(out, new java.util.Comparator<JSONObject>() {
                public int compare(JSONObject a, JSONObject b) {
                    if (fSort == 1) return b.optInt("enrollCount") - a.optInt("enrollCount");
                    if (fSort == 2) return Long.compare(a.optLong("enrollStartTime"), b.optLong("enrollStartTime"));
                    return Long.compare(a.optLong("startTime"), b.optLong("startTime"));
                }
            });
        }
        return out;
    }

    private String[] distinct(String field) {
        java.util.LinkedHashSet<String> s = new java.util.LinkedHashSet<>();
        for (int i = 0; i < allActivities.length(); i++) {
            JSONObject o = allActivities.optJSONObject(i);
            if (o == null) continue;
            String v = o.optString(field);
            if (!v.isEmpty()) s.add(v);
        }
        return s.toArray(new String[0]);
    }

    private void showClassFilter() {
        final String[] opts = distinct("classifyName");
        if (opts.length == 0) { toast("暂无可筛选的种类"); return; }
        boolean[] checked = new boolean[opts.length];
        for (int i = 0; i < opts.length; i++) checked[i] = fClass.contains(opts[i]);
        new AlertDialog.Builder(this).setTitle("按活动种类筛选")
                .setMultiChoiceItems(opts, checked, (d, w, is) -> { if (is) fClass.add(opts[w]); else fClass.remove(opts[w]); })
                .setPositiveButton("确定", (d, w) -> { updateFilterLabels(); renderActivities(); })
                .setNegativeButton("取消", null).show();
    }

    private void showStatusFilter() {
        final int[] vals = {4, 5, 6, 7};
        final String[] labs = {"报名未开始", "报名中", "进行中", "已结束"};
        boolean[] checked = new boolean[vals.length];
        for (int i = 0; i < vals.length; i++) checked[i] = fStatus.contains(vals[i]);
        new AlertDialog.Builder(this).setTitle("按状态筛选")
                .setMultiChoiceItems(labs, checked, (d, w, is) -> { if (is) fStatus.add(vals[w]); else fStatus.remove(vals[w]); })
                .setPositiveButton("确定", (d, w) -> { updateFilterLabels(); renderActivities(); })
                .setNegativeButton("取消", null).show();
    }

    private void showOrgFilter() {
        final String[] opts = distinct("organizationName");
        if (opts.length == 0) { toast("暂无可筛选的学院/组织"); return; }
        boolean[] checked = new boolean[opts.length];
        for (int i = 0; i < opts.length; i++) checked[i] = fOrg.contains(opts[i]);
        new AlertDialog.Builder(this).setTitle("按学院/组织筛选")
                .setMultiChoiceItems(opts, checked, (d, w, is) -> { if (is) fOrg.add(opts[w]); else fOrg.remove(opts[w]); })
                .setPositiveButton("确定", (d, w) -> { updateFilterLabels(); renderActivities(); })
                .setNegativeButton("取消", null).show();
    }

    private void showSortFilter() {
        final String[] opts = {"综合排序", "报名人数最多", "开抢时间最近", "活动时间最近"};
        new AlertDialog.Builder(this).setTitle("排序")
                .setSingleChoiceItems(opts, fSort, (d, w) -> { fSort = w; d.dismiss(); updateFilterLabels(); renderActivities(); })
                .show();
    }

    private void resetFilters() {
        fKeyword = ""; fClass.clear(); fStatus.clear(); fOrg.clear(); fSort = 0; fTime = 0;
        if (actSearch != null) actSearch.setText("");
        updateFilterLabels(); renderActivities();
    }

    private void updateFilterLabels() {
        if (btnClass == null) return;
        btnClass.setText(fClass.isEmpty() ? "种类" : "种类·" + fClass.size());
        btnOrg.setText(fOrg.isEmpty() ? "学院" : "学院·" + fOrg.size());
        String[] sorts = {"综合排序", "报名人数", "开抢时间", "活动时间"};
        btnSort.setText(fSort == 0 ? "排序" : sorts[fSort]);
        String[] times = {"时间", "今天", "本周", "本月"};
        if (btnTime != null) btnTime.setText(times[fTime]);
        updateStatusChips();
    }

    private long[] timeRange(int type) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        if (type == 1) {
            long s = c.getTimeInMillis();
            return new long[]{s, s + 24L * 3600 * 1000};
        }
        if (type == 2) {
            int dow = c.get(java.util.Calendar.DAY_OF_WEEK);
            int diff = (dow == java.util.Calendar.SUNDAY) ? 6 : dow - 2; // 周一为一周开始
            c.add(java.util.Calendar.DAY_OF_MONTH, -diff);
            long s = c.getTimeInMillis();
            return new long[]{s, s + 7L * 24 * 3600 * 1000};
        }
        c.set(java.util.Calendar.DAY_OF_MONTH, 1);
        long s = c.getTimeInMillis();
        c.add(java.util.Calendar.MONTH, 1);
        return new long[]{s, c.getTimeInMillis()};
    }

    // ---------------- 动作 ----------------
    private void queryOne(int id) {
        runBg(() -> {
            try {
                JSONObject d = api.detail(id);
                int st = d.optInt("status"), ap = d.optInt("applyStatus");
                String txt = "活动 " + id + " 状态:" + statusName(st) + " 名额:" + d.optInt("joinMemberCount") + "/" + d.optInt("peopleLimit")
                        + " 我:" + (ap == 2 ? "已报名" : "未报名");
                runOnUiThread(() -> tvStatus.setText(txt));
            } catch (Exception e) { runOnUiThread(() -> tvStatus.setText("查询失败：" + e.getMessage())); }
        });
    }

    /** 手动立即抢。 */
    private void manualEnroll(int id, String name) {
        new AlertDialog.Builder(this).setTitle("立即报名")
                .setMessage("确定立即为以下活动报名？\n" + name + "（id=" + id + "）")
                .setPositiveButton("立即报名", (d, w) -> runBg(() -> {
                    try {
                        JSONObject r = api.enroll(id, new JSONArray());
                        int code = r.optInt("code"); String msg = r.optString("msg");
                        boolean ok = code == 0 || msg.contains("已报") || msg.contains("重复");
                        final String out = ok ? "报名成功" : ("报名失败：" + msg + "（code=" + code + "）");
                        runOnUiThread(() -> { toast(out); tvStatus.setText(out); Notify.post(MainActivity.this, ok ? "✅ 报名成功" : "报名未成功", name + "：" + out, id, true); refreshMy(); });
                    } catch (Exception e) { runOnUiThread(() -> tvStatus.setText("报名失败：" + e.getMessage())); }
                }))
                .setNegativeButton("取消", null).show();
    }

    /** 取消报名。 */
    private void cancelEnroll(int id) {
        final EditText input = new EditText(this);
        input.setHint("取消原因（可留空）");
        new AlertDialog.Builder(this).setTitle("取消报名")
                .setMessage("确定取消活动 " + id + " 的报名吗？")
                .setView(input)
                .setPositiveButton("确定取消", (d, w) -> runBg(() -> {
                    try {
                        JSONObject r = api.cancel(id, input.getText().toString().trim());
                        int code = r.optInt("code"); String msg = r.optString("msg");
                        final String out = code == 0 ? "已取消报名" : ("取消失败：" + msg + "（code=" + code + "）");
                        runOnUiThread(() -> { toast(out); tvStatus.setText(out); Notify.post(MainActivity.this, "报名状态更新", "活动 " + id + "：" + out, id, true); refreshMy(); });
                    } catch (Exception e) { runOnUiThread(() -> tvStatus.setText("取消失败：" + e.getMessage())); }
                }))
                .setNegativeButton("再想想", null).show();
    }

    private void tryOne(int id) { manualEnroll(id, "活动 " + id); }

    private void showRecords(int activityId) {
        runBg(() -> {
            try {
                JSONArray arr = api.signRecords(myUserId, activityId);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject r = arr.optJSONObject(i);
                    if (r == null) continue;
                    sb.append("• ").append(r.optString("signTime", r.optString("createTime", r.optString("time", "")))).append("\n");
                }
                final String s = sb.length() == 0 ? "暂无签到/签退记录" : sb.toString();
                runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("打卡记录").setMessage(s).setPositiveButton("关闭", null).show());
            } catch (Exception e) { runOnUiThread(() -> tvStatus.setText("打卡记录获取失败：" + e.getMessage())); }
        });
    }

    /** 学生出示自己的签到/签退二维码，由工作人员扫描。 */
    private void showSignQr(int activityId) {
        if (myUserId == 0) {
            runBg(() -> {
                try {
                    JSONObject info = api.myInfo();
                    JSONObject d = info.optJSONObject("data");
                    if (d != null) myUserId = d.optInt("id", 0);
                } catch (Exception ignored) {}
                runOnUiThread(() -> showSignQr(activityId));
            });
            return;
        }
        final int size = dp(260);
        ImageView iv = new ImageView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        iv.setLayoutParams(lp);
        TextView tip = new TextView(this);
        tip.setTextSize(12); tip.setTextColor(cSub()); tip.setPadding(0, dp(10), 0, 0);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        box.setBackgroundColor(cCard());
        box.addView(iv); box.addView(tip);
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("请把此二维码出示给工作人员扫描")
                .setView(box).setPositiveButton("关闭", null).create();
        final Handler h = new Handler(Looper.getMainLooper());
        final Runnable refresh = new Runnable() {
            @Override public void run() {
                String content = "qutuo://waitSign?activityId=" + activityId + "&userId=" + myUserId
                        + "&sp=" + System.currentTimeMillis() + "?yiban=yiban_scan_result";
                Bitmap bm = qrBitmap(content, size);
                if (bm != null) {
                    iv.setImageBitmap(bm);
                    GradientDrawable wbg = new GradientDrawable();
                    wbg.setColor(0xFFFFFFFF); wbg.setCornerRadius(dp(8));
                    iv.setBackground(wbg);
                }
                tip.setText("活动 " + activityId + " · 二维码每 8 秒自动刷新\n保持本页打开，工作人员扫码即可完成签到/签退");
                if (dlg.isShowing()) h.postDelayed(this, 8000);
            }
        };
        dlg.setOnDismissListener(d -> h.removeCallbacks(refresh));
        dlg.show();
        h.post(refresh);
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

    private void openDetail(int id, String name) {
        Intent i = new Intent(this, ActivityDetailActivity.class);
        i.putExtra("id", id);
        i.putExtra("name", name);
        startActivity(i);
    }

    private void doLogout() {
        sp.edit().remove("token").remove("user").remove("pwd").apply();
        api.setToken(null);
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    /** 首次进入主页时，若未忽略电池优化，提示去做后台保活设置（只提示一次）。 */
    private void maybePromptKeepAlive() {
        try {
            if (sp.getBoolean("keepalive_prompted", false)) return;
            boolean ignore = true;
            if (Build.VERSION.SDK_INT >= 23) {
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
                ignore = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
            }
            if (!ignore) {
                new AlertDialog.Builder(this)
                        .setTitle("建议开启后台保活")
                        .setMessage("为保证报名结果通知能正常在后台弹出，建议开启电池优化白名单、自启动等权限。是否现在去设置？")
                        .setPositiveButton("去设置", (d, w) -> {
                            sp.edit().putBoolean("keepalive_prompted", true).apply();
                            startActivity(new Intent(this, KeepAliveActivity.class));
                        })
                        .setNegativeButton("稍后", (d, w) -> sp.edit().putBoolean("keepalive_prompted", true).apply())
                        .show();
            } else {
                sp.edit().putBoolean("keepalive_prompted", true).apply();
            }
        } catch (Throwable ignored) {}
    }

    // ---------------- 设置：深色/主题色/webVPN ----------------
    private void showSettings() {
        boolean vpn = sp.getBoolean("use_vpn", false);
        toast(Vpn.statusText(sp));
        String[] items = {
                dark ? "深色模式：开（点击关闭）" : "深色模式：关（点击开启）",
                "主题色：绿色", "主题色：蓝色", "主题色：紫色", "主题色：橙色",
                "webVPN 模式：" + (vpn ? "开（校外/流量）" : "关（直连校园网）"),
                "立即静默续期 webVPN（自动登录）",
                "手动重新登录 webVPN（校外/流量用）",
                "后台保活设置（通知 / 电池 / 自启动）"
        };
        new AlertDialog.Builder(this).setTitle("设置").setItems(items, (d, which) -> {
            if (which == 0) { sp.edit().putBoolean("dark", !dark).apply(); recreate(); }
            else if (which == 1) { sp.edit().putInt("accent", 0xFF0B8F6A).apply(); recreate(); }
            else if (which == 2) { sp.edit().putInt("accent", 0xFF2F6FED).apply(); recreate(); }
            else if (which == 3) { sp.edit().putInt("accent", 0xFF7B4FE0).apply(); recreate(); }
            else if (which == 4) { sp.edit().putInt("accent", 0xFFE6882F).apply(); recreate(); }
            else if (which == 5) {
                boolean nv = !vpn;
                sp.edit().putBoolean("use_vpn", nv).apply();
                if (nv && sp.getString("vpn_cookie", "").isEmpty()) {
                    startActivity(new Intent(this, VpnLoginActivity.class));
                } else {
                    recreate();
                }
            } else if (which == 6) {
                if (!Vpn.hasCreds(sp)) { toast("请先在登录页填写学号和密码"); return; }
                startActivityForResult(new Intent(this, VpnLoginActivity.class)
                        .putExtra(VpnLoginActivity.EXTRA_SILENT, true), REQ_VPN_RELOGIN);
            } else if (which == 7) {
                startActivityForResult(new Intent(this, VpnLoginActivity.class), REQ_VPN_RELOGIN);
            } else if (which == 8) {
                startActivity(new Intent(this, KeepAliveActivity.class));
            }
        }).show();
    }
}
