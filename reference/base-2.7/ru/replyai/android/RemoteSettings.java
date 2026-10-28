package ru.replyai.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.util.Base64;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/* JADX INFO: loaded from: classes.dex */
public final class RemoteSettings {
    private static final String ALIAS = "reply_ai_remote_v2";
    private final SharedPreferences prefs;

    public RemoteSettings(Context context) {
        this.prefs = context.getSharedPreferences("reply_ai_remote", 0);
        context.getSharedPreferences("reply_ai", 0).edit().putBoolean("enabled", false).apply();
    }

    public String url() {
        return this.prefs.getString("url", "");
    }

    public String token() {
        String string = this.prefs.getString("token", "");
        if (string.isEmpty()) {
            return "";
        }
        try {
            String[] strArrSplit = string.split(":", 2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(2, secret(), new GCMParameterSpec(128, Base64.decode(strArrSplit[0], 2)));
            return new String(cipher.doFinal(Base64.decode(strArrSplit[1], 2)), StandardCharsets.UTF_8);
        } catch (Exception unused) {
            return "";
        }
    }

    public void save(String str, String str2) throws Exception {
        String strNormalizeUrl = RemoteClient.normalizeUrl(str);
        RemoteClient.validateToken(str2);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(1, secret());
        if (!this.prefs.edit().putString("url", strNormalizeUrl).putString("token", Base64.encodeToString(cipher.getIV(), 2) + ":" + Base64.encodeToString(cipher.doFinal(str2.getBytes(StandardCharsets.UTF_8)), 2)).commit()) {
            throw new IOException("Не удалось сохранить подключение.");
        }
    }

    private static synchronized SecretKey secret() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(ALIAS)) {
            return (SecretKey) keyStore.getKey(ALIAS, null);
        }
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
        keyGenerator.init(new KeyGenParameterSpec.Builder(ALIAS, 3).setBlockModes("GCM").setEncryptionPaddings("NoPadding").build());
        return keyGenerator.generateKey();
    }
}
