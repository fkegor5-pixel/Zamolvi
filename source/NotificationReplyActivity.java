package ru.replyai.android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;

/** «Ответы из уведомлений»: auto-replies on the phone without connecting Telegram to the VPS. */
public final class NotificationReplyActivity extends FeatureActivity {
    private Switch enabled;
    private Switch everyone;
    private android.widget.TextView background;
    private LinearLayout answered;
    private LinearLayout ignored;
    private final java.util.ArrayList<android.view.View> owned = new java.util.ArrayList<>();
    private EditText chats;
    private final java.util.ArrayList<Switch> apps = new java.util.ArrayList<>();
    private android.widget.TextView access;
    private android.widget.TextView last;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        title("Ответы из уведомлений");
        text("Автоответ без входа в Telegram на сервере. Телефон видит уведомление о новом сообщении, сервер "
                + "составляет ответ выбранной нейросетью, и он отправляется кнопкой «Ответить» прямо из уведомления.", 15);
        text("Отвечает только в личных чатах и только пока телефон включён и в сети. Служебный чат «Telegram» "
                + "(коды входа) не трогается никогда. В Telegram "
                + "должен быть виден текст сообщений в уведомлениях (Настройки → Уведомления → «Показывать текст»). "
                + "На сервер уходит только текст сообщения, имя чата и аккаунт не передаются.", 14);
        SharedPreferences p = getSharedPreferences(NotificationReplyService.PREFS, 0);
        LinearLayout box = section();
        heading(box, "1. Доступ к уведомлениям");
        access = label(box, "", 14, true);
        action(box, "Открыть настройки доступа", v -> startActivity(
                new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)), new java.util.ArrayList<>());
        heading(box, "2. Работа в фоне");
        background = label(box, "", 14, true);
        action(box, "Снять ограничение батареи", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (RuntimeException noScreen) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        }, new java.util.ArrayList<>());
        action(box, "Автозапуск и фон (настройки приложения)", v -> startActivity(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + getPackageName()))),
                new java.util.ArrayList<>());
        heading(box, "3. Кому отвечать");
        everyone = toggle(box, "Всем личным чатам, кроме игнора", p.getBoolean(NotificationReplyService.ALL, false));
        label(box, "Если выключено — только чатам из списка: имена точно как в уведомлениях Telegram, по одному на строку.", 13, true);
        chats = field(box, "Например: Анна", p.getString(NotificationReplyService.CHATS, ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        chats.setSingleLine(false);
        chats.setMinLines(3);
        heading(box, "4. Мессенджеры");
        label(box, "В каких приложениях отвечать на уведомления. Имя чата берётся из уведомления.", 13, true);
        for (String[] app : Rules.APPS) {
            Switch row = toggle(box, app[1], p.getBoolean("app_" + app[0], "telegram".equals(app[0])));
            row.setTag(app[0]);
            apps.add(row);
        }
        label(box, "SMS: отвечаем только тем, кто назван в списке чатов (режим «всем» к SMS не относится); "
                + "ответ по SMS оплачивает ваш оператор. Сообщения с кодами подтверждения на сервер не уходят никогда.", 13, true);
        enabled = toggle(body, "Отвечать из уведомлений", p.getBoolean(NotificationReplyService.ENABLED, false));
        button("Сохранить", v -> save());
        button("Проверить ответ сервера", v -> execute(() -> request("POST", "/api/notification/answer",
                new org.json.JSONObject().put("text", "Привет! Как дела?")),
                value -> status.setText("Сервер ответил: " + value.optString("answer"))));
        controls.add(chats);
        button("Профили: Работаю · Сплю · За рулём", v -> startActivity(new Intent(this, ProfilesActivity.class)));
        button("Лимит, быстрые ответы, срочное", v -> startActivity(new Intent(this, RulesActivity.class)));
        status = text("", 15);
        last = text("", 13);
        text("Кому ответил", 20).setTypeface(null, android.graphics.Typeface.BOLD);
        answered = section();
        text("Игнор", 20).setTypeface(null, android.graphics.Typeface.BOLD);
        ignored = section();
    }

    @Override protected void onResume() {
        super.onResume();
        boolean granted = granted();
        access.setText(granted ? "Доступ есть."
                : "Доступа нет: включите Zamolvi в списке. Если переключатель неактивен — «О приложении» → ⋮ → "
                        + "«Разрешить ограниченные настройки» (Android 13+), затем снова сюда.");
        background.setText(NotificationReplyService.unrestricted(this)
                ? "Ограничение батареи снято."
                : "Батарея ограничивает приложение: в фоне ответы не уходят. Нажмите кнопку ниже и выберите «Разрешить». "
                        + "На TECNO/Infinix ещё: настройки приложения → «Батарея» → «Без ограничений», включите "
                        + "автозапуск и закрепите Zamolvi замком в списке недавних приложений.");
        SharedPreferences p = getSharedPreferences(NotificationReplyService.PREFS, 0);
        String sent = p.getString(NotificationReplyService.LAST, ""), seen = p.getString(NotificationReplyService.TRACE, "");
        last.setText((sent.isEmpty() ? "Ответов ещё не было." : "Последний ответ: " + sent)
                + (seen.isEmpty() ? "\nУведомлений Telegram ещё не было — напишите себе с другого аккаунта при закрытом чате."
                        : "\nПоследнее пропущенное уведомление: " + seen));
        showLists();
    }

    private void showLists() {
        SharedPreferences p = getSharedPreferences(NotificationReplyService.PREFS, 0);
        release(owned);
        answered.removeAllViews();
        ignored.removeAllViews();
        java.util.List<String> ignore = lines(p.getString(NotificationReplyService.IGNORE, ""));
        java.util.List<org.json.JSONObject> rows = new java.util.ArrayList<>();
        try {
            org.json.JSONObject all = new org.json.JSONObject(p.getString(NotificationReplyService.HISTORY, "{}"));
            for (java.util.Iterator<String> i = all.keys(); i.hasNext(); ) {
                String name = i.next();
                rows.add(all.getJSONObject(name).put("name", name));
            }
        } catch (org.json.JSONException ignoredError) {
            // statistics only: an unreadable list is shown as empty
        }
        rows.sort((a, b) -> Long.compare(b.optLong("at"), a.optLong("at")));
        if (rows.isEmpty()) label(answered, "Пока никому.", 14, true);
        for (org.json.JSONObject row : rows) {
            String name = row.optString("name");
            LinearLayout card = card(answered);
            heading(card, name);
            label(card, "Ответов: " + row.optInt("count") + " · последний " + time(row.optLong("at") / 1000.0), 13, true);
            if (!ignore.contains(name)) action(card, "В игнор", v -> setIgnored(name, true), owned);
            else label(card, "В игноре", 13, true);
        }
        if (ignore.isEmpty()) label(ignored, "Пусто. Нажмите «В игнор» у собеседника, чтобы больше ему не отвечать.", 14, true);
        for (String name : ignore) {
            LinearLayout card = card(ignored);
            heading(card, name);
            action(card, "Убрать из игнора", v -> setIgnored(name, false), owned);
        }
    }

    private void setIgnored(String name, boolean add) {
        SharedPreferences p = getSharedPreferences(NotificationReplyService.PREFS, 0);
        java.util.List<String> ignore = lines(p.getString(NotificationReplyService.IGNORE, ""));
        ignore.remove(name);
        if (add) ignore.add(name);
        p.edit().putString(NotificationReplyService.IGNORE, String.join("\n", ignore)).apply();
        status.setText(add ? "«" + name + "» в игноре: ему больше не отвечаем." : "«" + name + "» убран из игнора.");
        showLists();
    }

    private static java.util.List<String> lines(String value) {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String line : value.split("\n")) if (!line.trim().isEmpty()) result.add(line.trim());
        return result;
    }

    private void save() {
        String list = chats.getText().toString().trim();
        boolean on = enabled.isChecked(), all = everyone.isChecked();
        SharedPreferences.Editor editor = getSharedPreferences(NotificationReplyService.PREFS, 0).edit();
        for (Switch row : apps) editor.putBoolean("app_" + row.getTag(), row.isChecked());
        editor.putString(NotificationReplyService.CHATS, list)
                .putBoolean(NotificationReplyService.ALL, all)
                .putBoolean(NotificationReplyService.ENABLED, on).apply();
        status.setText(!on ? "Выключено."
                : !granted() ? "Сохранено, но нет доступа к уведомлениям — откройте настройки доступа."
                : !all && list.isEmpty() ? "Сохранено, но список чатов пуст — отвечать некому."
                : "Включено. Ответы приходят с пометкой «[AI-автоответ]».");
    }

    private boolean granted() {
        return NotificationReplyService.granted(this);
    }
}
