package ru.replyai.android;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import org.json.JSONObject;

/** Internal developer tools, unlocked by three taps. Never prints credentials. */
public final class DeveloperActivity extends FeatureActivity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!getSharedPreferences("replyai_features", 0).getBoolean("developer", false)) { finish(); return; }
        title("Режим разработчика");
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            text(Brand.NAME + " " + info.versionName + " (" + info.getLongVersionCode() + ")\nAndroid " + Build.VERSION.RELEASE
                    + " · API " + Build.VERSION.SDK_INT + "\nПакет: " + getPackageName()
                    + "\nОтладчик Android: " + (((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) ? "включён" : "выключен"), 15).setTextIsSelectable(true);
        } catch (Exception e) { text("Сведения о сборке недоступны", 15); }
        text("Проверка VPS, поддержка архива и отчёт об ошибках. Ключи и тексты переписки в результат проверки не включаются.", 15);
        status = text("Выберите проверку.", 15);
        status.setTextIsSelectable(true);
        button("Проверить подключение к VPS", v -> execute(() -> {
            long start = android.os.SystemClock.elapsedRealtime();
            JSONObject response = request("GET", "/api/status", null);
            response.put("elapsed_ms", android.os.SystemClock.elapsedRealtime() - start);
            return response;
        }, response -> status.setText("VPS отвечает · " + response.getLong("elapsed_ms") + " мс"
                + "\nВерсия сервера: " + response.optString("version", "неизвестно")
                + "\nTelegram: " + (response.optBoolean("authorized") ? "авторизован" : "не подключён")
                + "\nСоединение Telegram: " + (response.optBoolean("connected") ? "есть" : "нет")
                + "\nАвтоответчик: " + (response.optBoolean("enabled") ? "включён" : "выключен")
                + "\nВыбрано чатов: " + response.optInt("selected")
                + "\nВ очереди: " + response.optInt("queued"))));
        button("Проверить поддержку архива", v -> execute(() -> request("GET", "/api/archive", null), response ->
                status.setText("Сервер поддерживает архив"
                        + "\nСохранение: " + (response.optBoolean("enabled") ? "включено" : "выключено")
                        + "\nСрок хранения: " + response.optInt("retention_days") + " дней"
                        + "\nУдалённых сообщений: " + response.optInt("total"))));
        button("Сохранить диагностику", v -> startActivity(new Intent().setClassName(this, "ru.replyai.android.DiagnosticsActivity")));
        button("Открыть архив сообщений", v -> startActivity(new Intent(this, DeletedMessagesActivity.class)));
        button("Выключить режим разработчика", v -> {
            getSharedPreferences("replyai_features", 0).edit().remove("developer").apply();
            finish();
        });
        text("Для повторного открытия трижды нажмите логотип в главном меню. Для выключения используйте кнопку выше.", 14);
    }
}
