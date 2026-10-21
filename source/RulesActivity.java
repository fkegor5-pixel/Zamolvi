package ru.replyai.android;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** «Лимит, быстрые ответы, срочное»: расходы нейросети, ответы без неё и сообщения, на которые отвечаете вы сами. */
public final class RulesActivity extends FeatureActivity {
    private EditText limit;
    private EditText quick;
    private EditText stop;
    private TextView used;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        title("Лимит, быстрые ответы, срочное");
        text("Эти правила работают в ответах из уведомлений и проверяются раньше нейросети.", 15);
        SharedPreferences p = getSharedPreferences(NotificationReplyService.PREFS, 0);

        LinearLayout box = card(body);
        heading(box, "Лимит ответов нейросети в день");
        label(box, "Когда лимит исчерпан, до полуночи на сообщения отвечаете вы. 0 — без лимита. "
                + "Быстрые ответы ниже в лимит не входят.", 13, true);
        limit = field(box, "0", String.valueOf(p.getInt(NotificationReplyService.LIMIT, 0)), InputType.TYPE_CLASS_NUMBER);
        used = label(box, "", 13, true);

        box = card(body);
        heading(box, "Быстрые ответы");
        label(box, "Одна строка — одно правило: слово=ответ. Если слово есть в сообщении, уходит этот текст, "
                + "нейросеть не вызывается и ничего не стоит. Пример: цена=Цена по запросу, напишите, что нужно.", 13, true);
        quick = field(box, "цена=Цена по запросу", p.getString(NotificationReplyService.QUICK, ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        quick.setSingleLine(false);
        quick.setMinLines(4);

        box = card(body);
        heading(box, "Срочное — отвечаю сам");
        label(box, "Слова по одному в строке. Если они есть в сообщении, бот молчит, а вам приходит громкое "
                + "уведомление «Ответьте сами», которое открывает мессенджер. Пример: срочно, деньги, позвони.", 13, true);
        stop = field(box, "срочно", p.getString(NotificationReplyService.STOP, ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        stop.setSingleLine(false);
        stop.setMinLines(3);

        button("Сохранить", v -> save());
        status = text("", 15);
    }

    @Override protected void onResume() {
        super.onResume();
        SharedPreferences p = getSharedPreferences(NotificationReplyService.PREFS, 0);
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(new java.util.Date());
        int n = today.equals(p.getString(NotificationReplyService.COUNT_DAY, "")) ? p.getInt(NotificationReplyService.COUNT, 0) : 0;
        used.setText("Сегодня ответов нейросети: " + n);
    }

    private void save() {
        int value;
        try {
            String raw = limit.getText().toString().trim();
            value = raw.isEmpty() ? 0 : Integer.parseInt(raw);
        } catch (NumberFormatException error) {
            status.setText("Лимит — целое число, 0 без лимита.");
            return;
        }
        getSharedPreferences(NotificationReplyService.PREFS, 0).edit()
                .putInt(NotificationReplyService.LIMIT, Math.max(0, value))
                .putString(NotificationReplyService.QUICK, quick.getText().toString().trim())
                .putString(NotificationReplyService.STOP, stop.getText().toString().trim()).apply();
        status.setText("Сохранено.");
    }
}
