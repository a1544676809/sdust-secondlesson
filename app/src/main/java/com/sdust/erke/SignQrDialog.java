package com.sdust.erke;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/**
 * 「我的签到/签退二维码」弹窗（由工作人员扫码）。
 *
 * 与原版网页前端对齐：
 *  - 二维码里的 sp 必须用**服务器时间戳**（来自 /activity/sign/count 的 timestamp）再随真实时间递增，
 *    不能用手机本地时间（手机时钟若与服务器有偏差，工作人员扫码会失败且无声无息）。
 *  - 二维码类型：未签到(isSigner!=1) → qutuo://waitSign（签到码）；已签到(isSigner==1) → qutuo://sign（签退码）。
 *
 * 新增回执：每 2 秒轮询 /activity/detail/participant 的 isSigner / signInCount / signOutCount，
 * 给用户「签到成功 / 签退成功 / 尚未收到回执」的实时反馈。
 */
public final class SignQrDialog {

    private SignQrDialog() {}

    private static int dp(Activity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    private static String hms() {
        return new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                .format(new java.util.Date());
    }

    private static Bitmap qrBitmap(String content, int size) {
        try {
            com.google.zxing.qrcode.QRCodeWriter w = new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix m = w.encode(content,
                    com.google.zxing.BarcodeFormat.QR_CODE, size, size);
            int[] px = new int[size * size];
            for (int y = 0; y < size; y++)
                for (int x = 0; x < size; x++)
                    px[y * size + x] = m.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            bmp.setPixels(px, 0, size, 0, 0, size, size);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    public static void show(final Activity act, final Api api, final int activityId,
                            final int userId, final Runnable onChanged) {
        if (userId == 0) {
            Toast.makeText(act, "正在获取账号信息，请稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        final int size = dp(act, 250);

        final ImageView iv = new ImageView(act);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        iv.setLayoutParams(lp);

        final TextView status = new TextView(act);
        status.setTextSize(15);
        status.setPadding(0, dp(act, 12), 0, 0);
        status.setTextColor(0xFF333333);
        status.setText("正在获取签到状态…");

        final TextView tip = new TextView(act);
        tip.setTextSize(12);
        tip.setTextColor(0xFF888888);
        tip.setPadding(0, dp(act, 6), 0, 0);

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 16));
        box.setBackgroundColor(0xFFFFFFFF);
        box.addView(iv);
        box.addView(status);
        box.addView(tip);

        final Runnable[] pollRef = new Runnable[1];

        AlertDialog.Builder builder = new AlertDialog.Builder(act)
                .setTitle("请把此二维码出示给工作人员扫描")
                .setView(box)
                .setPositiveButton("关闭", null)
                .setNeutralButton("刷新状态", (d, w) -> {
                    if (pollRef[0] != null) pollRef[0].run();
                });
        final AlertDialog dlg = builder.create();

        final Handler h = new Handler(Looper.getMainLooper());

        // 服务器时间基准（毫秒）与本地开启时刻，用于推算 sp = 服务器基准 + 真实经过时间
        final long[] serverBase = {0L};
        final long[] openedAt = {System.currentTimeMillis()};
        final boolean[] signedIn = {false};
        final int[] baseIn = {-1}, baseOut = {-1};
        final boolean[] baseSigner = {false};
        final boolean[] done = {false};

        // 二维码刷新
        final Runnable refreshQr = new Runnable() {
            @Override public void run() {
                if (!dlg.isShowing()) return;
                long base = serverBase[0] > 0 ? serverBase[0] : openedAt[0];
                long sp = base + (System.currentTimeMillis() - openedAt[0]);
                String kind = signedIn[0] ? "sign" : "waitSign";
                String content = "qutuo://" + kind + "?activityId=" + activityId
                        + "&userId=" + userId + "&sp=" + sp + "?yiban=yiban_scan_result";
                Bitmap bm = qrBitmap(content, size);
                if (bm != null) {
                    iv.setImageBitmap(bm);
                    GradientDrawable wbg = new GradientDrawable();
                    wbg.setColor(0xFFFFFFFF);
                    wbg.setCornerRadius(dp(act, 8));
                    iv.setBackground(wbg);
                }
                if (dlg.isShowing()) h.postDelayed(this, 8000);
            }
        };
        pollRef[0] = refreshQr;

        // 状态轮询
        final Runnable poll = new Runnable() {
            @Override public void run() {
                if (!dlg.isShowing() || done[0]) return;
                new Thread(() -> {
                    JSONObject d = null;
                    String err = null;
                    try {
                        d = api.detail(activityId);
                    } catch (Exception e) {
                        err = e.getMessage();
                    }
                    final JSONObject dd = d;
                    final String eerr = err;
                    h.post(() -> {
                        if (!dlg.isShowing() || done[0]) return;
                        if (dd == null) {
                            status.setTextColor(0xFFE67E22);
                            status.setText("状态查询失败，正在重试…（" + eerr + "）");
                            h.postDelayed(this, 3000);
                            return;
                        }
                        int in = dd.optInt("signInCount", 0);
                        int out = dd.optInt("signOutCount", 0);
                        boolean signer = dd.optInt("isSigner", 0) == 1;
                        signedIn[0] = signer;

                        if (baseIn[0] < 0) {
                            baseIn[0] = in; baseOut[0] = out; baseSigner[0] = signer;
                            if (signer) {
                                status.setTextColor(0xFF0BBF9A);
                                status.setText("✅ 已签到（如需签退可再让工作人员扫码）");
                            } else {
                                status.setTextColor(0xFF333333);
                                status.setText("等待工作人员扫码…（二维码请保持打开）");
                            }
                        } else {
                            boolean signInOk = in > baseIn[0] || (!baseSigner[0] && signer && in == baseIn[0]);
                            boolean signOutOk = out > baseOut[0];
                            if (signOutOk) {
                                done[0] = true;
                                status.setTextColor(0xFF0BBD69);
                                status.setText("✅ 签退成功  " + hms());
                                toast(act, "✅ 签退成功");
                                Notify.post(act, "✅ 签退成功",
                                        "活动 " + activityId + " 签退成功（" + hms() + "）", activityId, false);
                                vibrate(act);
                                if (onChanged != null) onChanged.run();
                                return;
                            } else if (signInOk && !baseSigner[0]) {
                                baseSigner[0] = true;
                                baseIn[0] = Math.max(baseIn[0], in);
                                status.setTextColor(0xFF0BBD69);
                                status.setText("✅ 签到成功  " + hms() + "（如需签退，请再次出示）");
                                toast(act, "✅ 签到成功");
                                Notify.post(act, "✅ 签到成功",
                                        "活动 " + activityId + " 签到成功（" + hms() + "）", activityId, false);
                                vibrate(act);
                                if (onChanged != null) onChanged.run();
                                // 继续轮询，等待可能的签退
                            } else {
                                long sec = (System.currentTimeMillis() - openedAt[0]) / 1000;
                                if (sec >= 20) {
                                    status.setTextColor(0xFFE67E22);
                                    status.setText("尚未收到签到回执…请确认工作人员已成功扫码");
                                } else {
                                    status.setTextColor(0xFF333333);
                                    status.setText("等待工作人员扫码…（" + sec + "s）");
                                }
                            }
                        }
                        if (!done[0]) h.postDelayed(this, 2000);
                    });
                }).start();
            }
        };

        // 打开时：拿服务器时间(+全场签到数) & 我的签到状态，然后开始刷新与轮询
        new Thread(() -> {
            long ts = 0;
            int allIn = -1, member = -1;
            try {
                JSONObject r = api.signCount(activityId);
                ts = r.optLong("timestamp", 0);
                JSONObject data = r.optJSONObject("data");
                if (data != null) { allIn = data.optInt("signInCount", -1); member = data.optInt("memberCount", -1); }
            } catch (Exception ignored) {}
            final long fts = ts; final int fAllIn = allIn; final int fMember = member;
            try {
                JSONObject dd = api.detail(activityId);
                if (dd != null) {
                    signedIn[0] = dd.optInt("isSigner", 0) == 1;
                    baseSigner[0] = signedIn[0];
                    baseIn[0] = dd.optInt("signInCount", 0);
                    baseOut[0] = dd.optInt("signOutCount", 0);
                }
            } catch (Exception ignored) {}
            h.post(() -> {
                if (fts > 0) serverBase[0] = fts;
                StringBuilder t = new StringBuilder();
                t.append("活动 ").append(activityId);
                if (fAllIn >= 0 && fMember > 0) t.append(" · 全场已签到 ").append(fAllIn).append("/").append(fMember);
                t.append("\n请保持本页打开，工作人员扫码即可完成签到/签退");
                tip.setText(t.toString());
                if (signedIn[0]) {
                    status.setTextColor(0xFF0BBF9A);
                    status.setText("✅ 已签到（如需签退可再让工作人员扫码）");
                } else {
                    status.setTextColor(0xFF333333);
                    status.setText("等待工作人员扫码…（二维码请保持打开）");
                }
                h.post(refreshQr);
                h.postDelayed(poll, 600);
            });
        }).start();

        dlg.setOnDismissListener(x -> {
            h.removeCallbacks(refreshQr);
            h.removeCallbacks(poll);
        });
        dlg.show();
    }

    private static void toast(Activity a, String s) {
        Toast.makeText(a, s, Toast.LENGTH_LONG).show();
    }

    private static void vibrate(Activity a) {
        try {
            Vibrator v = (Vibrator) a.getSystemService(Activity.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(200);
        } catch (Throwable ignored) {}
    }
}
