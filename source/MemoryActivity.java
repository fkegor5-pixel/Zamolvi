package ru.replyai.android;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** «Память собеседников»: short summary per chat and «Забыть память этого чата». */
public final class MemoryActivity extends FeatureActivity {
    private Switch enabled;
    private LinearLayout list;
    private boolean known;
    private boolean changing;
    private final ArrayList<View> owned = new ArrayList<>();
    private final android.os.Handler poll = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refresh = this::load;
    private int[] limits = {100, 5000};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        title("Память собеседников");
        text("Автоответчик запоминает главное о каждом собеседнике: имя, интересы, договорённости и важные "
                + "факты — и учитывает это в следующих ответах. Запоминается только то, что сказал сам собеседник: "
                + "ваши собственные сообщения в память не попадают. Резюме обновляется после каждых трёх ответов "
                + "или через 30 минут.", 15);
        text("Источники — переписка, на которую ответил автоответчик, и история чата, если вы загрузите её кнопкой "
                + "в карточке собеседника. Память хранится в зашифрованном виде на VPS и удаляется вместе с чатом, "
                + "по команде /ai_off, при выходе из Telegram или кнопкой «Забыть». Пароли, коды, номера карт и "
                + "документов, точные адреса, сведения о здоровье, интимной жизни, политических и религиозных "
                + "взглядах не сохраняются.", 14);
        enabled = new Switch(this);
        enabled.setText("Запоминать собеседников");
        enabled.setTextColor(colors.ink);
        enabled.setPadding(0, dp(12), 0, dp(12));
        body.addView(enabled);
        controls.add(enabled);
        enabled.setOnCheckedChangeListener((view, checked) -> {
            if (changing) return;
            if (checked) save(true);
            else confirm("Выключить память?", "Память всех собеседников будет удалена.", "Выключить",
                    () -> save(false), () -> checked(true));
        });
        status = text("", 15);
        button("Обновить", v -> load());
        list = section();
        button("Забыть всех собеседников", v -> confirm("Забыть всех?",
                "Память всех собеседников будет удалена без возможности восстановления.", "Забыть", () -> forget(""), null));
        load();
    }

    private void load() {
        execute(() -> request("GET", "/api/memory", null), this::render);
    }

    private void save(boolean value) {
        execute(() -> request("PUT", "/api/memory", new JSONObject().put("enabled", value)), this::render);
    }

    private void forget(String chat) {
        execute(() -> request("POST", "/api/memory/forget", new JSONObject().put("chat", chat)), value -> {
            render(value);
            status.setText(chat.isEmpty() ? "Память всех собеседников удалена." : "Память этого чата удалена.");
        });
    }

    private void startImport(String chat, String name, int limit) {
        confirm("Загрузить переписку в память?",
                "Последние " + limit + " сообщений чата с «" + name + "» будут отправлены выбранному AI-провайдеру частями, "
                        + "чтобы дополнить память: имя, интересы, договорённости, факты. Сама переписка на сервере не "
                        + "сохраняется — остаётся только резюме. Это отдельный платный расход AI.\n\n"
                        + "Нажимая «Загрузить», вы подтверждаете, что собеседник согласен на передачу прошлой переписки в AI.",
                "Загрузить", () -> execute(() -> request("POST", "/api/memory/import", new JSONObject()
                        .put("chat", chat).put("limit", limit).put("consent", true)), this::render), null);
    }

    private void pickLimit(String chat, String name) {
        int[] options = {2000, 5000, 10000, limits[1]};
        String[] labels = new String[options.length];
        for (int i = 0; i < options.length; i++) labels[i] = "Последние " + options[i] + " сообщений";
        new android.app.AlertDialog.Builder(this).setTitle("Сколько сообщений загрузить?")
                .setItems(labels, (d, which) -> startImport(chat, name, options[which]))
                .setNegativeButton("Отмена", null).show();
    }

    private String importText(JSONObject job) {
        String state = job.optString("status");
        int parts = job.optInt("parts"), done = job.optInt("parts_done");
        switch (state) {
            case "loading": return "Загрузка истории: скачано сообщений " + job.optInt("downloaded") + " из " + job.optInt("limit") + "…";
            case "waiting": return "Загрузка истории: пауза по лимиту Telegram или AI — продолжу автоматически (обработано частей "
                    + job.optInt("parts_done") + " из " + job.optInt("parts") + ").";
            case "processing": return "Загрузка истории: обработано частей " + done + " из " + parts
                    + " (" + job.optInt("messages") + " сообщений). Ответы собеседникам идут в первую очередь.";
            case "done": return job.optString("error").isEmpty()
                    ? "История загружена: " + job.optInt("messages") + " сообщений, " + parts + " частей."
                            + (job.optInt("skipped") > 0 ? " Пропущено частей (модель не приняла): " + job.optInt("skipped") + "." : "")
                    : job.optString("error");
            case "cancelled": return "Загрузка остановлена: " + job.optString("error");
            default: return "Загрузка не удалась: " + job.optString("error");
        }
    }

    @Override protected void onDestroy() {
        poll.removeCallbacks(refresh);
        super.onDestroy();
    }

    private void checked(boolean value) {
        changing = true;
        enabled.setChecked(value);
        changing = false;
    }

    @Override protected void showError(Exception error) {
        checked(known);
        super.showError(error);
    }

    private void render(JSONObject value) throws Exception {
        known = value.getBoolean("enabled");
        checked(known);
        release(owned);
        list.removeAllViews();
        JSONArray chats = value.getJSONArray("chats");
        JSONArray range = value.optJSONArray("import_limits");
        if (range != null && range.length() == 2) limits = new int[] {range.getInt(0), range.getInt(1)};
        boolean running = false;
        status.setText(known
                ? "Память включена · Чатов: " + chats.length()
                : chats.length() == 0 ? "Сначала выберите личные чаты в главном меню." : "Память выключена.");
        for (int i = 0; i < chats.length(); i++) {
            JSONObject row = chats.getJSONObject(i);
            String chat = row.getString("chat");
            LinearLayout card = card(list);
            String who = row.optString("name", chat);
            heading(card, who);
            JSONObject summary = row.getJSONObject("summary");
            JSONObject job = row.optJSONObject("import");
            String jobState = job == null ? "" : job.optString("status");
            boolean active = "loading".equals(jobState) || "processing".equals(jobState) || "waiting".equals(jobState);
            running |= active;
            if (job != null) label(card, importText(job), 13, !active).setTextIsSelectable(true);
            if (known && active) {
                action(card, "Остановить загрузку", v -> execute(() -> request("POST", "/api/memory/import/cancel",
                        new JSONObject().put("chat", chat)), this::render), owned);
            } else if (known) {
                action(card, "Загрузить всю переписку в память", v -> pickLimit(chat, who), owned);
            }
            if (row.optBoolean("empty")) {
                label(card, known ? "Пока пусто: память появится после первых ответов или загрузки переписки." : "Пусто.", 14, true);
                continue;
            }
            String name = summary.optString("name");
            if (!name.isEmpty()) label(card, "Имя: " + name, 15, false);
            list(card, "Интересы", summary.optJSONArray("interests"));
            list(card, "Договорённости", summary.optJSONArray("agreements"));
            list(card, "Важные факты", summary.optJSONArray("facts"));
            int pending = row.optInt("pending");
            double updated = row.optDouble("updated_at", 0);
            label(card, (updated > 0 ? "Обновлено: " + time(updated) : "Резюме ещё не составлено")
                    + (pending > 0 ? " · Новых ответов до обновления: " + pending : ""), 12, true);
            action(card, "Забыть память этого чата", v -> confirm("Забыть память чата?",
                    "Всё, что автоответчик запомнил о собеседнике «" + row.optString("name", chat) + "», будет удалено.",
                    "Забыть", () -> forget(chat), null), owned);
        }
        poll.removeCallbacks(refresh);
        if (running) poll.postDelayed(refresh, 3000); // progress of a running import
    }

    private void list(LinearLayout card, String title, JSONArray items) throws Exception {
        if (items == null || items.length() == 0) return;
        StringBuilder text = new StringBuilder(title).append(':');
        for (int i = 0; i < items.length(); i++) text.append("\n• ").append(items.getString(i));
        label(card, text, 15, false).setTextIsSelectable(true);
    }
}
