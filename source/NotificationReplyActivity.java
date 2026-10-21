package ru.replyai.android;

import android.app.NotificationManager;
import android.content.ComponentName;
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
    private EditText chats;
    private android.widget.TextView access;
    private android.widget.TextView last;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        title("Ответы из уведомлений");
        text("Автоответ без входа в Telegram на сервере. Телефон видит уведомление о новом сообщении, сервер "
                + "составляет ответ выбранной нейросетью, и он отправляется кнопкой «Ответить» прямо из уведомления.", 15);
        text("Работает только для личных чатов из списка ниже и только пока телефон включён и в сети. В Telegram "
                + "должен быть виден текст сообщений в уведомлениях (Настройки → Уведомления → «Показывать текст»). "
                + "На сервер уходит только текст сообщения, имя чата и аккаунт не передаются.", 14);
        SharedPreferences p = getSharedPreferences(NotificationReplyService.PREFS, 0);
        LinearLayout box = section();
        heading(box, "1. Доступ к уведомлениям");
        access = label(box, "", 14, true);
        action(box, "Открыть настройки доступа", v -> startActivity(
                new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)), new java.util.ArrayList<>());
        heading(box, "2. Кому отвечать");
        label(box, "Имена чатов точно как в уведомлениях Telegram, по одному на строку. Остальным приложение не отвечает.", 13, true);
        chats = field(box, "Например: Анна", p.getString(NotificationReplyService.CHATS, ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        chats.setSingleLine(false);
        chats.setMinLines(3);
        enabled = new Switch(this);
        enabled.setText("Отвечать из уведомлений");
        enabled.setTextColor(colors.ink);
        enabled.setPadding(0, dp(12), 0, dp(12));
        enabled.setChecked(p.getBoolean(NotificationReplyService.ENABLED, false));
        body.addView(enabled);
        controls.add(enabled);
        button("Сохранить", v -> save());
        controls.add(chats);
        status = text("", 15);
        last = text("", 13);
    }

    @Override protected void onResume() {
        super.onResume();
        boolean granted = granted();
        access.setText(granted ? "Доступ есть."
                : "Доступа нет: включите Zamolvi в списке. Если переключатель неактивен — «О приложении» → ⋮ → "
                        + "«Разрешить ограниченные настройки» (Android 13+), затем снова сюда.");
        last.setText(getSharedPreferences(NotificationReplyService.PREFS, 0).getString(NotificationReplyService.LAST, ""));
    }

    private void save() {
        String list = chats.getText().toString().trim();
        boolean on = enabled.isChecked();
        getSharedPreferences(NotificationReplyService.PREFS, 0).edit()
                .putString(NotificationReplyService.CHATS, list)
                .putBoolean(NotificationReplyService.ENABLED, on).apply();
        status.setText(!on ? "Выключено."
                : !granted() ? "Сохранено, но нет доступа к уведомлениям — откройте настройки доступа."
                : list.isEmpty() ? "Сохранено, но список чатов пуст — отвечать некому."
                : "Включено. Ответы приходят в Telegram с пометкой «[AI-автоответ]».");
    }

    private boolean granted() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        return nm != null && nm.isNotificationListenerAccessGranted(new ComponentName(this, NotificationReplyService.class));
    }
}
