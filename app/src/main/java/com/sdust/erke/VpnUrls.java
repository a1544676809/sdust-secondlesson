package com.sdust.erke;

import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * webVPN（网瑞达）地址改写：把直连 URL 变成
 * https://webvpn.sdust.edu.cn/{http|https}/{keyHex}{AES-128-CFB(host) hex}/{path}。
 * 编码参数与网页端一致：key = iv = "wrdvpnisthebest!"，无填充。
 */
public final class VpnUrls {
    private static final String KEY = "wrdvpnisthebest!";
    private static final String KEY_HEX = "77726476706e69737468656265737421";
    private static final String PORTAL = "https://webvpn.sdust.edu.cn";

    private VpnUrls() {}

    public static String rewrite(String url) {
        if (url == null || url.isEmpty() || url.contains("webvpn.sdust.edu.cn")) return url;
        URL u;
        try {
            u = new URL(url);
        } catch (Exception e) {
            return url;
        }
        String scheme = u.getProtocol() == null ? "" : u.getProtocol().toLowerCase();
        if (!"http".equals(scheme) && !"https".equals(scheme)) return url;
        String host = u.getHost();
        if (host == null || host.isEmpty()) return url;
        String port = (u.getPort() != -1 && u.getPort() != u.getDefaultPort()) ? ":" + u.getPort() : "";
        String file = u.getFile();
        if (file == null || file.isEmpty()) file = "/";
        String encrypted = encrypt(host + port);
        if (encrypted == null) return url;
        return PORTAL + "/" + scheme + "/" + KEY_HEX + encrypted + file;
    }

    private static String encrypt(String text) {
        try {
            byte[] key = KEY.getBytes(StandardCharsets.UTF_8);
            Cipher cipher = Cipher.getInstance("AES/CFB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key));
            byte[] out = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                int v = b & 0xFF;
                if (v < 16) sb.append('0');
                sb.append(Integer.toHexString(v));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
