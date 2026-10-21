package ru.replyai.android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** «Обновление»: сравнивает установленную версию с последним выпуском на GitHub. */
public final class UpdateActivity extends FeatureActivity {
    private TextView state;
    private TextView notes;
    private Switch daily;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        title("Обновление");
        text("Приложение раз в день спрашивает у GitHub, нет ли версии новее, и показывает уведомление, "
                + "если она появилась. Проверка только сообщает о версии: APK вы скачиваете со страницы "
                + "выпуска и устанавливаете сами, как и раньше. На GitHub не уходит ничего, кроме самого запроса.", 15);
        LinearLayout box = card(body);
        heading(box, "Версия");
        state = label(box, "", 15, false);
        notes = label(box, "", 14, true);
        daily = toggle(body, "Проверять раз в день", prefs().getBoolean(Updates.ENABLED, true));
        daily.setOnCheckedChangeListener((v, on) -> {
            prefs().edit().putBoolean(Updates.ENABLED, on).apply();
            if (on) UpdateJob.schedule(this);
            status.setText(on ? "Проверка включена." : "Проверка выключена. Остаётся кнопка ниже.");
        });
        button("Проверить обновление", v -> execute(() -> {
            Updates.check(this);
            return new org.json.JSONObject();
        }, value -> {
            show();
            status.setText(ready() ? "Есть новая версия." : "У вас последняя версия.");
        }));
        button("Открыть страницу выпуска", v -> open());
        status = text("", 15);
    }

    @Override protected void onResume() {
        super.onResume();
        show();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(Updates.PREFS, 0);
    }

    private boolean ready() {
        String latest = prefs().getString(Updates.LATEST, "");
        return !latest.isEmpty() && Updates.newer(Updates.installed(), latest);
    }

    private void show() {
        SharedPreferences p = prefs();
        String latest = p.getString(Updates.LATEST, ""), failure = p.getString(Updates.FAILED, "");
        String line = "Установлена: " + Updates.installed()
                + "\nНа GitHub: " + (latest.isEmpty() ? "неизвестно" : latest)
                + "\nПроверено: " + Updates.when(p.getLong(Updates.CHECKED_AT, 0));
        if (!failure.isEmpty()) line += "\nПоследняя проверка не удалась: " + failure;
        else if (ready()) line += "\nЕсть новая версия — откройте страницу выпуска.";
        else if (!latest.isEmpty()) line += "\nУ вас последняя версия.";
        state.setText(line);
        String text = p.getString(Updates.NOTES, "");
        notes.setText(text.isEmpty() ? "" : "Что нового:\n" + text);
    }

    /** The release page in the browser; its own address, nothing is downloaded by the application. */
    private void open() {
        String page = prefs().getString(Updates.PAGE, Updates.LATEST_PAGE);
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(page))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException noBrowser) {
            status.setText("Не удалось открыть браузер. Адрес: " + page);
        }
    }
}
