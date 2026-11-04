package com.sdust.erke;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 后台保活引导页：引导用户开启通知权限、忽略电池优化、允许精确闹钟，并给出各品牌"自启动/后台运行"的设置路径。
 * 这些都是"本地通知/提醒"在国产 ROM 上能否准时工作的关键。
 */
public class KeepAliveActivity extends Activity {
    private SharedPreferences sp;
    private boolean dark;
    private int accent;
    private LinearLayout list;

    private static final int REQ_NOTIF = 21;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("erke", MODE_PRIVATE);
        dark = sp.getBoolean("dark", false);
        accent = sp.getInt("accent", 0xFF0B8F6A);
        build();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private int cBg() { return dark ? 0xFF121417 : 0xFFF2F4F7; }
    private int cCard() { return dark ? 0xFF1E2228 : 0xFFFFFFFF; }
    private int cText() { return dark ? 0xFFECECEC : 0xFF222222; }
    private int cSub() { return dark ? 0xFF9AA0A6 : 0xFF777777; }

    private GradientDrawable cardBg() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(cCard());
        d.setCornerRadius(dp(12));
        return d;
    }

    private void build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(cBg());
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(22));
        sv.addView(root);
        setContentView(sv);

        TextView back = new TextView(this);
        back.setText("\u2039 返回");
        back.setTextSize(14);
        back.setTextColor(accent);
        back.setPadding(0, 0, 0, dp(8));
        back.setOnClickListener(v -> finish());
        root.addView(back);

        TextView title = new TextView(this);
        title.setText("后台保活设置");
        title.setTextSize(20);
        title.setTextColor(cText());
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("为保证「报名结果通知」等在被切到后台、锁屏、清理内存后仍能正常弹出，请把下面几项都打开。国产手机（小米/华为/OPPO/vivo 等）系统会限制后台，通常还需要手动允许“自启动/后台运行”，并在最近任务里锁定本应用。");
        desc.setTextSize(13);
        desc.setTextColor(cSub());
        desc.setPadding(0, dp(6), 0, dp(12));
        root.addView(desc);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        TextView vendor = new TextView(this);
        vendor.setText(vendorHint());
        vendor.setTextSize(12);
        vendor.setTextColor(cSub());
        vendor.setPadding(dp(4), dp(16), dp(4), dp(6));
        root.addView(vendor);

        Button openApp = new Button(this);
        openApp.setText("打开本应用设置（自启动/权限/通知都在这里）");
        openApp.setAllCaps(false);
        openApp.setOnClickListener(v -> openAppDetails());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        root.addView(openApp, lp);

        TextView foot = new TextView(this);
        foot.setText("提示：不同品牌路径不一致，本页只能帮你一键打开系统对应页面；“自启动/后台运行/锁定后台”需要你在系统设置里手动允许。");
        foot.setTextSize(11);
        foot.setTextColor(cSub());
        foot.setPadding(dp(4), dp(12), dp(4), 0);
        root.addView(foot);
    }

    /** 刷新三块状态卡片（每次回到页面都会重算）。 */
    private void refresh() {
        boolean notifOk;
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                notifOk = checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
            } else {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                notifOk = nm != null && nm.areNotificationsEnabled();
            }
        } catch (Throwable t) { notifOk = false; }

        boolean battOk = true;
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                battOk = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
            }
        } catch (Throwable t) { battOk = false; }

        boolean exactOk = true;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
                exactOk = am == null || am.canScheduleExactAlarms();
            }
        } catch (Throwable t) { exactOk = false; }

        if (list == null) return;
        list.removeAllViews();

        addItem("通知权限", notifOk ? 1 : 0,
                notifOk ? "已开启" : "未开启，收不到结果通知",
                notifOk ? "去系统设置" : "去开启",
                v -> requestNotifPermission());

        addItem("忽略电池优化", battOk ? 1 : 0,
                battOk ? "已开启，后台不被省电限制" : "未开启，后台可能被杀",
                "去设置", v -> requestIgnoreBattery());

        addItem("精确闹钟", exactOk ? 1 : 0,
                exactOk ? "已允许，提醒更准时" : "未允许，通知可能延迟",
                "去允许", v -> requestExactAlarm());

        addItem("自启动 / 后台运行", -1,
                "需手动开启（各品牌路径不同，见下方说明）",
                "打开应用设置", v -> openAppDetails());

        addItem("最近任务锁定（可选）", -1,
                "在最近任务里下拉/长按本应用卡片 → 点“锁定”",
                null, null);
    }

    /** state: 1=已开启(绿) 0=未开启(橙) -1=需手动(灰) */
    private void addItem(String title, int state, String status, String btnText, View.OnClickListener onClick) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBg());
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        list.addView(card, lp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(15);
        t.setTextColor(cText());
        t.setTypeface(null, Typeface.BOLD);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(t);

        TextView badge = new TextView(this);
        badge.setTextSize(12);
        int bc;
        if (state == 1) bc = 0xFF0B8F6A;
        else if (state == 0) bc = 0xFFE6882F;
        else bc = cSub();
        badge.setTextColor(bc);
        badge.setText(state == 1 ? "✔ 已开启" : (state == 0 ? "✘ 未开启" : "需手动"));
        head.addView(badge);
        card.addView(head);

        TextView st = new TextView(this);
        st.setText(status);
        st.setTextSize(12);
        st.setTextColor(cSub());
        st.setPadding(0, dp(4), 0, 0);
        card.addView(st);

        if (btnText != null && onClick != null) {
            Button bt = new Button(this);
            bt.setText(btnText);
            bt.setAllCaps(false);
            bt.setTextSize(13);
            bt.setOnClickListener(onClick);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.topMargin = dp(8);
            card.addView(bt, blp);
        }
    }

    // ---------------- 权限申请 / 跳转 ----------------
    private void requestNotifPermission() {
        try {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
            } else {
                openNotificationSettings();
            }
        } catch (Throwable t) { openNotificationSettings(); }
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQ_NOTIF) refresh();
    }

    private void openNotificationSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        } catch (Throwable t) { openAppDetails(); }
    }

    private void requestIgnoreBattery() {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } else {
                openAppDetails();
            }
        } catch (Throwable t) {
            toast("请到 设置→电池→电池优化 里把本应用设为“不限制”");
            openAppDetails();
        }
    }

    private void requestExactAlarm() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } else {
                toast("当前系统无需单独授权精确闹钟");
            }
        } catch (Throwable t) { openAppDetails(); }
    }

    private void openAppDetails() {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable t) { toast("请到系统设置→应用→二课助手 手动设置"); }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    /** 按品牌给出“自启动/后台运行”的具体路径。 */
    private String vendorHint() {
        String m = (Build.MANUFACTURER == null ? "" : Build.MANUFACTURER).toLowerCase();
        String brandLine;
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) {
            brandLine = "小米/Redmi：设置→应用设置→应用管理→二课助手→自启动 打开；省电策略→无限制；最近任务里下拉卡片锁定。";
        } else if (m.contains("huawei") || m.contains("honor")) {
            brandLine = "华为/荣耀：手机管家→应用启动管理→找到二课助手→关闭“自动管理”→勾选 自启动/关联启动/后台活动。";
        } else if (m.contains("oppo") || m.contains("realme") || m.contains("oneplus")) {
            brandLine = "OPPO/realme/一加：设置→电池→应用耗电管理→二课助手→允许后台运行；设置→应用管理→自启动 打开。";
        } else if (m.contains("vivo") || m.contains("iqoo")) {
            brandLine = "vivo/iQOO：设置→电池→后台耗电管理→二课助手→允许后台高耗电；i管家→应用管理→自启动/后台运行 打开。";
        } else if (m.contains("meizu")) {
            brandLine = "魅族：手机管家→权限管理→后台管理→二课助手→允许后台运行；设置→应用管理→自启动 打开。";
        } else {
            brandLine = "通用：设置→应用→二课助手→电池→选择“不限制/允许后台活动”；再在系统的“自启动管理”里允许本应用。";
        }
        return "【" + (Build.MANUFACTURER == null ? "本机" : Build.MANUFACTURER) + "】" + brandLine;
    }
}
