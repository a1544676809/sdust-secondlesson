package com.sdust.erke;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;

/** 应用内消息中心：展示历史通知，点按可跳到对应活动详情。 */
public class NotificationActivity extends Activity {
    private SharedPreferences sp;
    private boolean dark = false;
    private int accent = 0xFF0B8F6A;
    private LinearLayout listBox;
    private BroadcastReceiver receiver;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("erke", MODE_PRIVATE);
        dark = sp.getBoolean("dark", false);
        accent = sp.getInt("accent", 0xFF0B8F6A);
        buildUi();
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { render(); }
        };
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(Notify.ACTION_HISTORY);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);
        render();
        Notify.markAllRead(this);
    }
    @Override protected void onStop() {
        super.onStop();
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private int cBg() { return dark ? 0xFF121417 : 0xFFF2F4F7; }
    private int cCard() { return dark ? 0xFF1E2228 : 0xFFFFFFFF; }
    private int cText() { return dark ? 0xFFECECEC : 0xFF222222; }
    private int cSub() { return dark ? 0xFF9AA0A6 : 0xFF777777; }
    private int cAccent() { return accent; }

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
        back.setText("‹ 返回"); back.setTextSize(15); back.setTextColor(cAccent());
        back.setPadding(0, dp(4), dp(14), dp(4)); back.setOnClickListener(v -> finish());
        TextView title = new TextView(this);
        title.setText("消息中心"); title.setTextSize(17); title.setTextColor(cText()); title.setTypeface(null, Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView clear = new TextView(this);
        clear.setText("清空"); clear.setTextSize(13); clear.setTextColor(cAccent());
        clear.setOnClickListener(v -> { Notify.clear(this); render(); });
        bar.addView(back); bar.addView(title); bar.addView(clear);
        root.addView(bar);

        ScrollView sc = new ScrollView(this);
        sc.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(dp(14), dp(12), dp(14), dp(12));
        sc.addView(listBox);
        root.addView(sc);
    }

    private void render() {
        listBox.removeAllViews();
        JSONArray arr = Notify.history(this);
        if (arr.length() == 0) {
            TextView t = new TextView(this);
            t.setText("暂无消息"); t.setTextColor(cSub()); t.setTextSize(13);
            t.setPadding(dp(4), dp(24), 0, 0);
            listBox.addView(t);
            return;
        }
        SimpleDateFormat df = new SimpleDateFormat("MM-dd HH:mm");
        for (int i = arr.length() - 1; i >= 0; i--) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable g = new GradientDrawable();
            g.setColor(cCard()); g.setCornerRadius(dp(14));
            c.setBackground(g);
            c.setPadding(dp(14), dp(12), dp(14), dp(12));
            c.setElevation(dp(3));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10); c.setLayoutParams(lp);
            TextView t1 = new TextView(this);
            t1.setText(o.optString("t")); t1.setTextSize(15); t1.setTextColor(cText()); t1.setTypeface(null, Typeface.BOLD);
            TextView t2 = new TextView(this);
            t2.setText(o.optString("m")); t2.setTextSize(13); t2.setTextColor(cSub()); t2.setPadding(0, dp(6), 0, 0);
            TextView t3 = new TextView(this);
            t3.setText(df.format(new Date(o.optLong("ts", System.currentTimeMillis())))); t3.setTextSize(11); t3.setTextColor(cSub()); t3.setPadding(0, dp(6), 0, 0);
            c.addView(t1); c.addView(t2); c.addView(t3);
            final int aid = o.optInt("id", 0);
            if (aid > 0) c.setOnClickListener(v -> {
                Intent it = new Intent(this, ActivityDetailActivity.class);
                it.putExtra("id", aid);
                startActivity(it);
            });
            listBox.addView(c);
        }
    }
}
