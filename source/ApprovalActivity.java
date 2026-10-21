package ru.replyai.android;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import java.util.ArrayList;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONObject;

/** «Согласование ответов»: AI prepares a draft, the owner sends, edits or rejects it. */
public final class ApprovalActivity extends FeatureActivity {
    private RadioButton off;
    private RadioButton all;
    private RadioButton chosen;
    private LinearLayout chats;
    private CheckBox notify;
    private LinearLayout drafts;
    private final ArrayList<CheckBox> boxes = new ArrayList<>();
    private final ArrayList<View> ownedChats = new ArrayList<>();
    private final ArrayList<View> ownedDrafts = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        title("Согласование ответов");
        text("AI готовит черновик, а вы решаете: отправить как есть, исправить или отклонить. Пока черновик ждёт, "
                + "следующие ответы в этом чате не отправляются. Без решения черновик удаляется через 12 часов.", 15);
        RadioGroup group = new RadioGroup(this);
        off = radio(group, "Выключено — все ответы уходят автоматически");
        all = radio(group, "Все чаты");
        chosen = radio(group, "Только выбранные чаты");
        body.addView(group);
        chats = section();
        group.setOnCheckedChangeListener((g, id) -> chats.setVisibility(chosen.isChecked() ? View.VISIBLE : View.GONE));
        notify = new CheckBox(this);
        notify.setText("Сообщать о новом черновике в «Избранное» Telegram (без текста переписки)");
        notify.setTextColor(colors.ink);
        body.addView(notify);
        controls.add(notify);
        button("Сохранить режим", v -> save());
        status = text("", 15);
        text("Черновики", 20).setTypeface(null, android.graphics.Typeface.BOLD);
        button("Обновить черновики", v -> load());
        drafts = section();
        load();
    }

    private RadioButton radio(RadioGroup group, String label) {
        RadioButton view = new RadioButton(this);
        view.setText(label);
        view.setTextColor(colors.ink);
        view.setId(View.generateViewId());
        group.addView(view);
        controls.add(view);
        return view;
    }

    private void load() {
        execute(() -> request("GET", "/api/approval", null), this::render);
    }

    private void save() {
        String mode = all.isChecked() ? "all" : chosen.isChecked() ? "chats" : "off";
        JSONArray ids = new JSONArray();
        for (CheckBox box : boxes) if (box.isChecked()) ids.put((String) box.getTag());
        execute(() -> request("PUT", "/api/approval", new JSONObject().put("mode", mode).put("chats", ids)
                .put("notify", notify.isChecked())), value -> {
            render(value);
            status.setText("Режим сохранён.");
        });
    }

    private void decide(String id, String action, String text) {
        execute(() -> request("POST", "/api/approval/decide", new JSONObject().put("id", id).put("action", action)
                .put("text", text)), value -> {
            render(value);
            status.setText(value.optString("message"));
        });
    }

    private void render(JSONObject value) throws Exception {
        String mode = value.getString("mode");
        off.setChecked("off".equals(mode));
        all.setChecked("all".equals(mode));
        chosen.setChecked("chats".equals(mode));
        notify.setChecked(value.optBoolean("notify", true));
        HashSet<String> picked = new HashSet<>();
        JSONArray modeChats = value.getJSONArray("chats");
        for (int i = 0; i < modeChats.length(); i++) picked.add(modeChats.getString(i));
        release(ownedChats);
        boxes.clear();
        chats.removeAllViews();
        JSONArray selected = value.getJSONArray("selected");
        if (selected.length() == 0) label(chats, "Сначала выберите личные чаты в главном меню.", 14, true);
        for (int i = 0; i < selected.length(); i++) {
            JSONObject chat = selected.getJSONObject(i);
            CheckBox box = new CheckBox(this);
            box.setText(chat.optString("name"));
            box.setTag(chat.getString("id"));
            box.setTextColor(colors.ink);
            box.setChecked(picked.contains(chat.getString("id")));
            chats.addView(box);
            controls.add(box);
            ownedChats.add(box);
            boxes.add(box);
        }
        chats.setVisibility("chats".equals(mode) ? View.VISIBLE : View.GONE);

        release(ownedDrafts);
        drafts.removeAllViews();
        JSONArray rows = value.getJSONArray("drafts");
        if (rows.length() == 0) label(drafts, "Черновиков нет.", 14, true);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String id = row.getString("id");
            LinearLayout card = card(drafts);
            heading(card, row.optString("name"));
            label(card, "Сообщение собеседника:", 12, true);
            label(card, row.optString("incoming"), 15, false).setTextIsSelectable(true);
            label(card, "Ответ (можно исправить):", 12, true);
            EditText answer = field(card, "Текст ответа", row.optString("answer"),
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            answer.setMinLines(3);
            ownedDrafts.add(answer);
            label(card, "Удалится без ответа: " + time(row.optDouble("expires_at", 0)), 12, true);
            action(card, "Отправить", v -> decide(id, "send", answer.getText().toString()), ownedDrafts);
            action(card, "Отклонить", v -> confirm("Отклонить черновик?", "Ответ не будет отправлен.", "Отклонить",
                    () -> decide(id, "reject", ""), null), ownedDrafts);
        }
        status.setText(rows.length() > 0 ? "Ждут решения: " + rows.length() : "off".equals(mode)
                ? "Согласование выключено." : "Согласование включено.");
    }
}
