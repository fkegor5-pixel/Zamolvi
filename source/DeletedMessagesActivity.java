package ru.replyai.android;

import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.json.JSONArray;
import org.json.JSONObject;

public final class DeletedMessagesActivity extends FeatureActivity {
    private Switch enabled;
    private EditText search;
    private LinearLayout messages;
    private Button more;
    private int offset;
    private String query = "";
    private boolean knownEnabled;
    private boolean changing;
    private AlertDialog clearDialog;
    private final DateTimeFormatter date = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        title("Удалённые сообщения");
        text("Включите сохранение заранее. Архив хранит текст и подписи к вложениям в выбранных личных чатах, даже когда автоответчик выключен. Старые удалённые сообщения восстановить нельзя. Telegram не всегда сообщает об удалении.", 15);
        text("До 30 дней и 500 удалённых сообщений. До удаления сервер держит копии 500 последних сообщений. Фото, видео и аудиофайлы не скачиваются. Копии зашифрованы на VPS. Выход из Telegram или исключение чата удаляет его архив.", 14);
        enabled = new Switch(this);
        enabled.setText("Сохранять удалённые сообщения");
        enabled.setTextColor(colors.ink);
        enabled.setPadding(0, dp(12), 0, dp(12));
        body.addView(enabled);
        controls.add(enabled);
        enabled.setOnCheckedChangeListener((view, checked) -> {
            if (!changing) configure(checked);
        });
        status = text("", 15);
        status.setTextIsSelectable(true);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Поиск по тексту или собеседнику");
        search.setTextColor(colors.ink);
        search.setHintTextColor(colors.muted);
        search.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(80) });
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener((view, action, event) -> {
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) return false;
            searchNow();
            return true;
        });
        body.addView(search, new LinearLayout.LayoutParams(-1, -2));
        controls.add(search);
        button("Найти / обновить", v -> searchNow());
        button("Очистить архив", v -> confirmClear());
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        body.addView(messages, new LinearLayout.LayoutParams(-1, -2));
        more = button("Показать ещё", v -> load(true));
        more.setVisibility(android.view.View.GONE);
        load(false);
    }

    private void confirmClear() {
        clearDialog = new AlertDialog.Builder(this)
                .setTitle("Очистить архив?")
                .setMessage("Будут удалены сохранённые сообщения и временные копии. В Telegram ничего не изменится.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Очистить", (dialog, which) -> execute(() -> {
                    request("POST", "/api/archive/clear", new JSONObject());
                    return page(0);
                }, value -> render(value, false))).create();
        clearDialog.show();
    }

    @Override protected void onDestroy() {
        if (clearDialog != null) clearDialog.dismiss();
        super.onDestroy();
    }

    private void searchNow() {
        query = search.getText().toString().trim();
        load(false);
    }

    private JSONObject page(int start) throws Exception {
        return request("POST", "/api/archive/list", new JSONObject().put("offset", start).put("limit", 20).put("query", query));
    }
    private void load(boolean append) {
        int start = append ? offset : 0;
        execute(() -> page(start), value -> render(value, append));
    }
    private void configure(boolean checked) {
        execute(() -> {
            request("PUT", "/api/archive", new JSONObject().put("enabled", checked));
            return page(0);
        }, value -> render(value, false));
    }
    private void checked(boolean value) {
        changing = true;
        enabled.setChecked(value);
        changing = false;
    }
    @Override protected void showError(Exception error) {
        checked(knownEnabled);
        super.showError(error);
    }
    private void render(JSONObject value, boolean append) throws Exception {
        knownEnabled = value.getBoolean("enabled");
        checked(knownEnabled);
        if (!append) { messages.removeAllViews(); offset = 0; }
        JSONArray rows = value.getJSONArray("messages");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(14));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(colors.card); bg.setCornerRadius(dp(14));
            card.setBackground(bg);
            TextView heading = new TextView(this);
            heading.setText(row.optString("chat_name", row.getString("chat_id")) + " · " + (row.optBoolean("outgoing") ? "Исходящее" : "Входящее"));
            heading.setTypeface(null, Typeface.BOLD);
            heading.setTextColor(colors.ink);
            heading.setTextSize(16);
            card.addView(heading);
            TextView time = new TextView(this);
            time.setText("Отправлено: " + date.format(Instant.ofEpochSecond((long) row.getDouble("sent_at")))
                    + "\nУдалено: " + date.format(Instant.ofEpochSecond((long) row.getDouble("deleted_at")))
                    + (row.optBoolean("edited") ? "\nПоследняя полученная редакция" : ""));
            time.setTextSize(12); time.setTextColor(colors.muted); card.addView(time);
            TextView content = new TextView(this);
            String media = row.optString("media");
            String label = "voice".equals(media) ? "Голосовое сообщение" : "photo".equals(media) ? "Фото" : "video".equals(media) ? "Видео" : "Вложение";
            String text = row.optString("text");
            if (row.optBoolean("truncated")) text += "\n[Текст обрезан до 4096 символов]";
            if (!media.isEmpty()) text += (text.isEmpty() ? "" : "\n\n") + "[" + label + ": файл не сохранён]";
            content.setText(text.isEmpty() ? "[Сообщение без текста]" : text);
            content.setTextSize(16); content.setTextColor(colors.ink);
            content.setTextIsSelectable(true); content.setPadding(0, dp(8), 0, 0); card.addView(content);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(12);
            messages.addView(card, lp);
        }
        offset += rows.length();
        more.setVisibility(value.optBoolean("has_more") ? android.view.View.VISIBLE : android.view.View.GONE);
        status.setText((knownEnabled ? "Сохранение включено" : "Сохранение выключено")
                + "\nЧатов: " + value.getInt("selected_count") + " · Копий до удаления: " + value.getInt("cached_count")
                + "\nВ архиве: " + value.getInt("total") + (query.isEmpty() ? "" : " · Найдено: " + value.getInt("filtered_total"))
                + (value.getInt("filtered_total") == 0 ? "\nУдалённых сообщений пока нет." : ""));
    }
}
