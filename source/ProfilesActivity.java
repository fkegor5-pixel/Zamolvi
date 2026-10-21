package ru.replyai.android;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** «Профили»: Работаю, Сплю, За рулём — свой стиль и пояснение для нейросети, по времени или вручную. */
public final class ProfilesActivity extends FeatureActivity {
    private static final String LIKE_SAVED = "-"; // no style of its own: the one chosen in «Стиль общения» stays
    private final int count = Rules.PROFILES.length;
    private final Switch[] on = new Switch[count];
    private final EditText[] from = new EditText[count];
    private final EditText[] to = new EditText[count];
    private final EditText[] note = new EditText[count];
    private final Button[] styleButton = new Button[count];
    private final String[] style = new String[count];
    private final ArrayList<String[]> styles = new ArrayList<>(); // key, title from the server
    private TextView active;
    private AlertDialog picker;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        title("Профили");
        text("Профиль подсказывает нейросети, в какой вы сейчас ситуации, и может менять стиль ответов. "
                + "Он включается сам в заданное время или вручную переключателем. Работает для ответов из уведомлений "
                + "и требует сервер 3.3.0 или новее («15. Проверка функций» → обновить).", 15);
        active = text("", 15);
        SharedPreferences p = getSharedPreferences(NotificationReplyService.PREFS, 0);
        for (int i = 0; i < count; i++) {
            final int index = i;
            LinearLayout card = card(body);
            heading(card, Rules.PROFILES[i][0]);
            on[i] = toggle(card, "Включить сейчас", p.getBoolean("prof_" + i + "_on", false));
            label(card, "Или само, в промежутке времени (например 22:00 — 07:00). Пусто — только вручную.", 13, true);
            from[i] = field(card, "С — 22:00", p.getString("prof_" + i + "_from", ""), InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
            to[i] = field(card, "До — 07:00", p.getString("prof_" + i + "_to", ""), InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
            label(card, "Стиль на это время", 13, true);
            style[i] = p.getString("prof_" + i + "_style", LIKE_SAVED);
            styleButton[i] = action(card, "", v -> choose(index), new ArrayList<>());
            label(card, "Что сказать нейросети о ситуации", 13, true);
            note[i] = field(card, "", p.getString("prof_" + i + "_note", Rules.PROFILES[i][1]),
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            note[i].setSingleLine(false);
            note[i].setMinLines(3);
        }
        button("Сохранить", v -> save());
        status = text("", 15);
        showStyles();
        execute(() -> request("GET", "/api/style", null), value -> {
            styles.clear();
            JSONArray rows = value.getJSONArray("styles");
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                styles.add(new String[]{row.getString("key"), row.getString("title")});
            }
            showStyles();
            status.setText("");
        });
    }

    @Override protected void onResume() {
        super.onResume();
        int now = NotificationReplyService.activeProfile(getSharedPreferences(NotificationReplyService.PREFS, 0));
        active.setText(now < 0 ? "Сейчас ни один профиль не действует." : "Сейчас действует: " + Rules.PROFILES[now][0]);
    }

    private String titleOf(String key) {
        if (LIKE_SAVED.equals(key)) return "Как в разделе «Стиль общения»";
        for (String[] row : styles) if (row[0].equals(key)) return row[1];
        return key.isEmpty() ? "Обычный" : key;
    }

    private void showStyles() {
        for (int i = 0; i < count; i++) styleButton[i].setText("Стиль: " + I18n.t(this, titleOf(style[i])));
    }

    private void choose(int index) {
        ArrayList<String> keys = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        keys.add(LIKE_SAVED);
        names.add(I18n.t(this, titleOf(LIKE_SAVED)));
        for (String[] row : styles) { keys.add(row[0]); names.add(row[1]); }
        if (picker != null) picker.dismiss();
        picker = new AlertDialog.Builder(this)
                .setTitle(I18n.t(this, "Стиль на это время"))
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    style[index] = keys.get(which);
                    showStyles();
                })
                .create();
        picker.show();
    }

    private void save() {
        SharedPreferences.Editor editor = getSharedPreferences(NotificationReplyService.PREFS, 0).edit();
        for (int i = 0; i < count; i++) {
            String a = from[i].getText().toString().trim(), b = to[i].getText().toString().trim();
            if (a.isEmpty() != b.isEmpty()) {
                status.setText("«" + Rules.PROFILES[i][0] + "»: укажите оба времени или оставьте оба пустыми.");
                return;
            }
            if (!a.isEmpty() && (Rules.minutes(a) < 0 || Rules.minutes(b) < 0)) {
                status.setText("«" + Rules.PROFILES[i][0] + "»: время вводится как 22:00.");
                return;
            }
            editor.putBoolean("prof_" + i + "_on", on[i].isChecked()).putString("prof_" + i + "_from", a)
                    .putString("prof_" + i + "_to", b).putString("prof_" + i + "_style", style[i])
                    .putString("prof_" + i + "_note", note[i].getText().toString().trim());
        }
        editor.apply();
        onResume();
        status.setText("Сохранено.");
    }

    @Override protected void onDestroy() {
        if (picker != null) picker.dismiss();
        super.onDestroy();
    }
}
