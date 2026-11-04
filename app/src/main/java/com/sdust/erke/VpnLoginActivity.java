package com.sdust.erke;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 内置 webVPN 登录：WebView 完成统一认证，成功后保存 webVPN Cookie 供原生网络层使用。
 *
 * 两种模式：
 *  - 普通模式：显示登录页/表单，用户可手动完成（也自动填充）。
 *  - 静默模式（Intent extra "silent"=true）：不显示表单，仅显示“正在自动续期…”，
 *    用已保存账号密码自动登录；成功直接返回 RESULT_OK，失败(超时)返回 RESULT_CANCELED。
 */
public class VpnLoginActivity extends Activity {
    public static final String EXTRA_SILENT = "silent";
    private static final String VPN_HOME = "https://webvpn.sdust.edu.cn/";
    private SharedPreferences sp;
    private WebView web;
    private TextView status;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean done = false;
    private boolean autoFilled = false;
    private boolean silent = false;
    private long startAt = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("erke", MODE_PRIVATE);
        silent = getIntent().getBooleanExtra(EXTRA_SILENT, false);
        startAt = System.currentTimeMillis();
        buildUi();
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) cm.setAcceptThirdPartyCookies(web, true);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setUserAgentString(s.getUserAgentString());
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                if (!autoFilled && url != null && (url.contains("/cas/login") || url.contains("cas_login"))) autoFillCas();
                maybeAutoDone(url);
            }
        });
        web.loadUrl("https://webvpn.sdust.edu.cn/login?cas_login=true");
        h.postDelayed(poll, 1500);
        if (silent) h.postDelayed(silentTimeout, 30000);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(silent ? 0xFFFFFFFF : 0xFFF2F4F7);
        setContentView(root);

        web = new WebView(this);

        if (silent) {
            // 静默模式：只显示居中的“自动续期”提示；WebView 以 1x1 隐藏运行。
            LinearLayout center = new LinearLayout(this);
            center.setOrientation(LinearLayout.VERTICAL);
            center.setGravity(Gravity.CENTER);
            center.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            TextView t = new TextView(this);
            t.setText("正在自动续期 webVPN 登录…");
            t.setTextSize(15); t.setTextColor(0xFF0B8F6A); t.setTypeface(null, Typeface.BOLD);
            t.setGravity(Gravity.CENTER);
            status = new TextView(this);
            status.setText("无需操作，几秒后自动完成");
            status.setTextSize(12); status.setTextColor(0xFF888888); status.setGravity(Gravity.CENTER);
            status.setPadding(0, dp(8), 0, 0);
            center.addView(t); center.addView(status);
            root.addView(center);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(1), dp(1));
            web.setLayoutParams(lp);
            web.setAlpha(0.01f);
            root.addView(web);
            return;
        }

        // 普通模式
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(12), dp(14), dp(10));
        bar.setBackgroundColor(0xFFFFFFFF);
        TextView back = new TextView(this);
        back.setText("‹ 返回"); back.setTextSize(15); back.setTextColor(0xFF0B8F6A);
        back.setPadding(0, dp(4), dp(14), dp(4)); back.setOnClickListener(v -> finish());
        TextView title = new TextView(this);
        title.setText("webVPN 登录（校外/流量）"); title.setTextSize(16); title.setTextColor(0xFF222222);
        title.setTypeface(null, Typeface.BOLD);
        bar.addView(back); bar.addView(title);
        root.addView(bar);

        status = new TextView(this);
        status.setText("请在下方登录学校统一身份认证，登录成功会自动完成。");
        status.setTextSize(12); status.setTextColor(0xFF777777);
        status.setPadding(dp(14), dp(6), dp(14), dp(6));
        root.addView(status);

        web.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(web);

        Button doneBtn = new Button(this);
        doneBtn.setText("我已完成登录，继续");
        doneBtn.setAllCaps(false);
        doneBtn.setOnClickListener(v -> saveCookies());
        root.addView(doneBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
    }

    /** 用网页自带 JS 自动填 CAS 账号密码并提交（密码由页面 login.js 自己 RSA 加密）。 */
    private void autoFillCas() {
        String u = sp.getString("user", "");
        String p = sp.getString("pwd", "");
        if (u.isEmpty() || p.isEmpty()) return;
        autoFilled = true;
        try {
            String ju = org.json.JSONObject.quote(u);
            String jp = org.json.JSONObject.quote(p);
            String js = "(function(){try{var u=document.getElementById('username');"
                    + "var p=document.getElementById('ppassword');"
                    + "if(u&&p){u.value=" + ju + ";p.value=" + jp + ";"
                    + "if(typeof checkForm==='function'){checkForm();}"
                    + "else{var f=document.getElementById('fm1');if(f)f.submit();}}}catch(e){}})();";
            web.evaluateJavascript(js, null);
            if (!silent) status.setText("已自动填写账号，正在登录…");
        } catch (Exception ignored) {}
    }

    private void maybeAutoDone(String url) {
        try {
            String ck = CookieManager.getInstance().getCookie("https://webvpn.sdust.edu.cn");
            boolean hasTicket = ck != null && ck.contains("wengine_vpn_ticketwebvpn_sdust_edu_cn=");
            boolean notLogin = url != null && url.contains("webvpn.sdust.edu.cn")
                    && !url.contains("cas_login") && !url.contains("/cas/login");
            if (hasTicket && notLogin) saveCookies();
        } catch (Exception ignored) {}
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (done) return;
            h.postDelayed(this, 2000);
        }
    };

    private final Runnable silentTimeout = new Runnable() {
        @Override public void run() {
            if (done) return;
            if (silent) {
                // 静默续期失败：交给调用方兜底（会提示用户手动登录 webVPN）
                done = true;
                Toast.makeText(VpnLoginActivity.this, "webVPN 自动续期未完成，请手动登录", Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
                finish();
            }
        }
    };

    private void saveCookies() {
        if (done) return;
        try {
            String ck = CookieManager.getInstance().getCookie("https://webvpn.sdust.edu.cn");
            if (ck == null || !ck.contains("wengine_vpn_ticketwebvpn_sdust_edu_cn=")) {
                if (!silent) Toast.makeText(this, "还没检测到 webVPN 登录态，请先在页面里完成登录", Toast.LENGTH_LONG).show();
                return;
            }
            CookieManager.getInstance().flush();
            sp.edit().putString("vpn_cookie", ck).putBoolean("use_vpn", true).apply();
            Vpn.markSaved(sp);
            done = true;
            Toast.makeText(this, silent ? "webVPN 已自动续期" : "webVPN 登录已保存", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } catch (Exception e) {
            if (!silent) Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onDestroy() {
        h.removeCallbacks(poll);
        h.removeCallbacks(silentTimeout);
        try { if (web != null) web.destroy(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
