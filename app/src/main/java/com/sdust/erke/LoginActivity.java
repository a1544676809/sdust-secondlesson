package com.sdust.erke;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/** 独立登录页：本地有登录信息则自动登录，失败或未登录才展示登录表单。 */
public class LoginActivity extends Activity {
    private SharedPreferences sp;
    private Api api = new Api();
    private EditText etUser, etPwd;
    private TextView tvStatus;
    private Button btnLogin;
    private volatile boolean busy = false;
    private static final int REQ_VPN = 200;
    private static final int REQ_VPN_SILENT = 202;
    private TextView tvVpnStatus;
    private boolean vpnRefreshTried = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("erke", MODE_PRIVATE);
        api.setVpn(sp.getBoolean("use_vpn", false), sp.getString("vpn_cookie", ""));
        buildUi();
        if (!maybeSilentVpnRefresh()) attemptAutoLogin();
    }

    /** 启动时若 webVPN 会话快过期，先用已保存账号密码“静默续期”，再自动登录。 */
    private boolean maybeSilentVpnRefresh() {
        if (Vpn.needsRefresh(sp) && Vpn.hasCreds(sp)) {
            vpnRefreshTried = true;
            setBusy(true, "webVPN 会话即将过期，正在自动续期…");
            startActivityForResult(new Intent(this, VpnLoginActivity.class)
                    .putExtra(VpnLoginActivity.EXTRA_SILENT, true), REQ_VPN_SILENT);
            return true;
        }
        return false;
    }

    private void updateVpnStatus() {
        if (tvVpnStatus != null) tvVpnStatus.setText(Vpn.statusText(sp));
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(10);
        return p;
    }

    private void buildUi() {
        GradientDrawable bgGrad = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFE6F6EF, 0xFFF5F9FF});
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackground(bgGrad);
        setContentView(root);

        TextView logo = new TextView(this);
        logo.setText("二课");
        logo.setTextSize(22);
        logo.setTextColor(0xFFFFFFFF);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(null, Typeface.BOLD);
        GradientDrawable lg = new GradientDrawable();
        lg.setShape(GradientDrawable.OVAL);
        lg.setColor(0xFF0B8F6A);
        logo.setBackground(lg);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(76), dp(76));
        llp.gravity = Gravity.CENTER_HORIZONTAL;
        llp.bottomMargin = dp(14);
        logo.setLayoutParams(llp);
        root.addView(logo);

        TextView title = new TextView(this);
        title.setText("二课助手");
        title.setTextSize(24);
        title.setTextColor(0xFF0B8F6A);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("山东科技大学 · 第二课堂");
        sub.setTextSize(12);
        sub.setTextColor(0xFF888888);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(4), 0, dp(18));
        root.addView(sub);

        LinearLayout cardBox = new LinearLayout(this);
        cardBox.setOrientation(LinearLayout.VERTICAL);
        cardBox.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable cg = new GradientDrawable();
        cg.setColor(0xFFFFFFFF);
        cg.setCornerRadius(dp(18));
        cardBox.setBackground(cg);
        cardBox.setElevation(dp(6));
        cardBox.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(cardBox);

        etUser = new EditText(this);
        etUser.setHint("学号");
        etUser.setSingleLine(true);
        etUser.setText(sp.getString("user", ""));
        cardBox.addView(etUser, matchWrap());

        etPwd = new EditText(this);
        etPwd.setHint("统一身份认证密码");
        etPwd.setSingleLine(true);
        etPwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPwd.setText(sp.getString("pwd", ""));
        cardBox.addView(etPwd, matchWrap());

        btnLogin = new Button(this);
        btnLogin.setText("登 录");
        btnLogin.setTextColor(0xFFFFFFFF);
        btnLogin.setTextSize(15);
        btnLogin.setAllCaps(false);
        GradientDrawable bgb = new GradientDrawable();
        bgb.setColor(0xFF0B8F6A);
        bgb.setCornerRadius(dp(24));
        btnLogin.setBackground(bgb);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        blp.topMargin = dp(16);
        btnLogin.setLayoutParams(blp);
        btnLogin.setOnClickListener(v -> doLogin());
        cardBox.addView(btnLogin);

        tvStatus = new TextView(this);
        tvStatus.setTextSize(12);
        tvStatus.setTextColor(0xFF888888);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, dp(12), 0, 0);
        cardBox.addView(tvStatus);

        TextView vpnHint = new TextView(this);
        vpnHint.setText("校外 / 手机流量访问，请先登录 webVPN");
        vpnHint.setTextSize(12);
        vpnHint.setTextColor(0xFF888888);
        vpnHint.setGravity(Gravity.CENTER);
        vpnHint.setPadding(0, dp(16), 0, 0);
        root.addView(vpnHint);

        Button btnVpn = new Button(this);
        btnVpn.setText("使用 webVPN 登录（校外 / 手机流量）");
        btnVpn.setTextColor(0xFF0B8F6A);
        btnVpn.setTextSize(15);
        btnVpn.setAllCaps(false);
        GradientDrawable vbg = new GradientDrawable();
        vbg.setColor(0x00FFFFFF);
        vbg.setStroke(dp(2), 0xFF0B8F6A);
        vbg.setCornerRadius(dp(24));
        btnVpn.setBackground(vbg);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        vlp.topMargin = dp(10);
        btnVpn.setLayoutParams(vlp);
        btnVpn.setOnClickListener(v -> startVpnLogin());
        root.addView(btnVpn);

        tvVpnStatus = new TextView(this);
        tvVpnStatus.setTextSize(11);
        tvVpnStatus.setTextColor(0xFF888888);
        tvVpnStatus.setGravity(Gravity.CENTER);
        tvVpnStatus.setPadding(0, dp(10), 0, 0);
        root.addView(tvVpnStatus);
        updateVpnStatus();
    }

    private void setBusy(boolean b, String status) {
        busy = b;
        btnLogin.setEnabled(!b);
        btnLogin.setText(b ? "请稍候…" : "登 录");
        if (status != null) tvStatus.setText(status);
    }

    private void goMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    /** 有 token 先校验；无效则用保存的账号密码重新 CAS 登录；都没有才显示表单。 */
    private void attemptAutoLogin() {
        final String token = sp.getString("token", "");
        final String user = sp.getString("user", "");
        final String pwd = sp.getString("pwd", "");
        if (token.isEmpty() && (user.isEmpty() || pwd.isEmpty())) {
            tvStatus.setText("请登录");
            return;
        }
        setBusy(true, "检测到本地登录信息，正在自动登录…");
        new Thread(() -> {
            try {
                if (!token.isEmpty()) {
                    api.setToken(token);
                    JSONObject info = api.myInfo();
                    if (info.optInt("code", -1) == 0) { runOnUiThread(this::goMain); return; }
                }
                if (user.isEmpty() || pwd.isEmpty()) throw new Exception("本地没有完整账号密码");
                String t = api.login(user, pwd);
                sp.edit().putString("token", t).putString("user", user).putString("pwd", pwd).apply();
                runOnUiThread(this::goMain);
            } catch (Exception e) {
                final String m = e.getMessage();
                runOnUiThread(() -> {
                    if (looksBlocked(m)) {
                        // 网络被拦（多半是 webVPN 会话过期）：若已开启 VPN 且有账号密码，先自动静默续期一次
                        if (Vpn.on(sp) && Vpn.hasCreds(sp) && !vpnRefreshTried) {
                            vpnRefreshTried = true;
                            setBusy(true, "webVPN 会话可能已过期，正在自动续期…");
                            startActivityForResult(new Intent(this, VpnLoginActivity.class)
                                    .putExtra(VpnLoginActivity.EXTRA_SILENT, true), REQ_VPN_SILENT);
                        } else {
                            setBusy(false, "当前网络无法直连，请使用 webVPN 登录");
                            promptVpn();
                        }
                    } else setBusy(false, "自动登录失败，请手动登录：" + m);
                });
            }
        }).start();
    }

    private static boolean looksBlocked(String m) {
        if (m == null) return false;
        return m.contains("403") || m.contains("访问策略") || m.contains("返回网页")
                || m.contains("非JSON") || m.contains("<!DOCTYPE") || m.contains("返回非JSON");
    }

    private void promptVpn() {
        new AlertDialog.Builder(this)
                .setTitle("需要 webVPN")
                .setMessage("检测到当前网络（如手机流量）无法直连第二课堂，需要用学校 webVPN 登录一次。是否现在登录？")
                .setPositiveButton("去登录 webVPN", (d, w) -> startVpnLogin())
                .setNegativeButton("取消", null)
                .show();
    }

    private void startVpnLogin() {
        // 保存当前输入的账号密码，供 webVPN 登录页自动填充
        sp.edit().putString("user", etUser.getText().toString().trim())
                .putString("pwd", etPwd.getText().toString()).apply();
        startActivityForResult(new Intent(this, VpnLoginActivity.class), REQ_VPN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN) {
            if (resultCode == RESULT_OK) {
                api.setVpn(true, sp.getString("vpn_cookie", ""));
                setBusy(true, "webVPN 已登录，正在重试…");
                attemptAutoLogin();
            } else {
                setBusy(false, "已取消 webVPN 登录");
            }
        } else if (requestCode == REQ_VPN_SILENT) {
            api.setVpn(Vpn.on(sp), Vpn.cookie(sp));
            updateVpnStatus();
            if (resultCode == RESULT_OK) {
                setBusy(true, "webVPN 已自动续期，正在登录…");
            } else {
                setBusy(true, "webVPN 续期未完成，正在尝试直连/登录…");
            }
            attemptAutoLogin();
        }
    }

    private void doLogin() {
        if (busy) return;
        final String u = etUser.getText().toString().trim();
        final String p = etPwd.getText().toString();
        if (u.isEmpty() || p.isEmpty()) { Toast.makeText(this, "请填写学号和密码", Toast.LENGTH_SHORT).show(); return; }
        setBusy(true, "登录中…");
        new Thread(() -> {
            try {
                String t = api.login(u, p);
                sp.edit().putString("token", t).putString("user", u).putString("pwd", p).apply();
                runOnUiThread(this::goMain);
            } catch (Exception e) {
                final String m = e.getMessage();
                runOnUiThread(() -> {
                    if (looksBlocked(m)) { setBusy(false, "当前网络无法直连，请使用 webVPN 登录"); promptVpn(); }
                    else setBusy(false, "登录失败：" + m);
                });
            }
        }).start();
    }
}
