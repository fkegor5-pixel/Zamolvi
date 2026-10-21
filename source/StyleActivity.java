package ru.replyai.android;

import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import org.json.JSONArray;
import org.json.JSONObject;

/** «Стиль общения»: a tone preset added to the AI instruction for every reply. */
public final class StyleActivity extends FeatureActivity {
    private RadioGroup group;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        title("Стиль общения");
        text("Каким тоном отвечает нейросеть. Стиль добавляется к вашей инструкции из «Нейросеть и текст» и "
                + "действует для всех ответов: в Telegram на сервере, из уведомлений и в проверке «Проверить». "
                + "Смысл ответов не меняется — только манера.", 15);
        group = new RadioGroup(this);
        body.addView(group);
        status = text("", 15);
        button("Проверить ответ", v -> execute(() -> request("POST", "/api/notification/answer",
                new JSONObject().put("text", "Привет! Как дела, чем занимаешься?")),
                value -> status.setText("Пример ответа: " + value.optString("answer"))));
        execute(() -> request("GET", "/api/style", null), this::render);
    }

    private void render(JSONObject value) throws Exception {
        group.setOnCheckedChangeListener(null);
        group.removeAllViews();
        JSONArray styles = value.getJSONArray("styles");
        String current = value.optString("style");
        for (int i = 0; i < styles.length(); i++) {
            JSONObject row = styles.getJSONObject(i);
            RadioButton radio = new RadioButton(this);
            radio.setId(i + 1);
            radio.setText(row.getString("title") + " — " + row.optString("description"));
            radio.setTextColor(colors.ink);
            radio.setPadding(0, dp(8), 0, dp(8));
            radio.setTag(row.getString("key"));
            group.addView(radio);
            controls.add(radio);
            if (row.getString("key").equals(current)) radio.setChecked(true);
        }
        group.setOnCheckedChangeListener((g, id) -> {
            String key = (String) g.findViewById(id).getTag();
            execute(() -> request("PUT", "/api/style", new JSONObject().put("style", key)), saved -> {
                status.setText("Стиль сохранён. Нажмите «Проверить ответ», чтобы увидеть пример.");
            });
        });
    }
}
