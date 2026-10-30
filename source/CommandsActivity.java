package ru.replyai.android;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** «Свои команды»: a word, where to look and how to answer; the connected model writes the answer. */
public final class CommandsActivity extends FeatureActivity {
    private static final int TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    private static final int MULTI = TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE;

    private JSONArray commands = new JSONArray();
    private int editing = -1;  // index of the command in the editor; -1 — a new one
    private LinearLayout list;
    private LinearLayout editor;
    private EditText word, sources, task, query;
    private TextView result;
    private final ArrayList<View> owned = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        title("⚡ Свои команды");
        text("Собеседник пишет слово команды — нейросеть читает указанные вами сайты или каналы и отвечает так, "
                + "как вы описали. Работает в выбранных чатах Telegram и в «Ответах из уведомлений». "
                + "Нужен свой сервер и подключённая нейросеть.", 15);
        status = text("", 14);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list, new LinearLayout.LayoutParams(-1, -2));
        button("＋  Новая команда", v -> edit(-1));

        editor = card(body);
        heading(editor, "Команда");
        word = field(editor, "Слово команды, например: курс биткоина", "", TEXT);
        label(editor, "Где искать — по одной ссылке в строке: сайт https://… или канал @имя. "
                + "{запрос} в ссылке заменится словами после команды. Можно оставить пустым.", 13, true);
        sources = field(editor, "https://www.cbr.ru\n@durov\nhttps://ru.wikipedia.org/w/index.php?search={запрос}", "", MULTI);
        sources.setMinLines(3);
        label(editor, "Как ответить — задание для нейросети.", 13, true);
        task = field(editor, "Найди курс биткоина в рублях и ответь одной строкой с датой", "", MULTI);
        task.setMinLines(3);
        label(editor, "Пример слов после команды (для проверки {запрос}).", 13, true);
        query = field(editor, "Например: Алматы", "", TEXT);
        button(editor, "👁  Как будет выглядеть", v -> preview());
        result = label(editor, "", 15, false);
        button(editor, "Сохранить", v -> save());
        button(editor, "Отмена", v -> editor.setVisibility(View.GONE));
        editor.setVisibility(View.GONE);

        execute(() -> request("GET", "/api/commands", null), this::render);
    }

    private void button(LinearLayout parent, String value, View.OnClickListener listener) {
        action(parent, value, listener, new ArrayList<>());
    }

    private void render(JSONObject value) throws Exception {
        commands = value.getJSONArray("commands");
        release(owned);
        list.removeAllViews();
        status.setText(commands.length() == 0 ? "Команд пока нет." : value.optString("message"));
        for (int i = 0; i < commands.length(); i++) {
            JSONObject c = commands.getJSONObject(i);
            LinearLayout box = card(list);
            heading(box, c.getString("word"));
            int n = c.getJSONArray("sources").length();
            label(box, (n == 0 ? "Без источников" : "Источников: " + n) + " · " + c.getString("task"), 14, true);
            final int index = i;
            action(box, "Изменить", v -> edit(index), owned);
            action(box, "Удалить", v -> confirm("Удалить команду?", c.optString("word"), "Удалить",
                    () -> remove(index), null), owned);
        }
    }

    private void edit(int index) {
        editing = index;
        JSONObject c = index < 0 ? new JSONObject() : commands.optJSONObject(index);
        word.setText(c.optString("word"));
        task.setText(c.optString("task"));
        JSONArray s = c.optJSONArray("sources");
        StringBuilder lines = new StringBuilder();
        for (int i = 0; s != null && i < s.length(); i++) lines.append(i > 0 ? "\n" : "").append(s.optString(i));
        sources.setText(lines);
        result.setText("");
        editor.setVisibility(View.VISIBLE);
        word.requestFocus();
    }

    private JSONObject current() throws Exception {
        JSONArray s = new JSONArray();
        for (String line : sources.getText().toString().split("\n")) {
            if (!line.trim().isEmpty()) s.put(line.trim());
        }
        return new JSONObject().put("word", word.getText().toString().trim())
                .put("sources", s).put("task", task.getText().toString().trim());
    }

    private void preview() {
        result.setText("Нейросеть отвечает…");
        execute(() -> request("POST", "/api/commands/preview",
                new JSONObject().put("command", current()).put("text", query.getText().toString().trim())),
                value -> {
                    result.setText("Так ответит бот на «" + (word.getText().toString().trim() + " "
                            + query.getText().toString().trim()).trim() + "»:\n\n" + value.optString("answer"));
                    status.setText("");
                });
    }

    private void save() {
        execute(() -> {
            JSONArray next = new JSONArray();
            for (int i = 0; i < commands.length(); i++) next.put(i == editing ? current() : commands.get(i));
            if (editing < 0) next.put(current());
            return request("PUT", "/api/commands", new JSONObject().put("commands", next));
        }, value -> {
            editor.setVisibility(View.GONE);
            render(value);
        });
    }

    private void remove(int index) {
        execute(() -> {
            JSONArray next = new JSONArray();
            for (int i = 0; i < commands.length(); i++) if (i != index) next.put(commands.get(i));
            return request("PUT", "/api/commands", new JSONObject().put("commands", next));
        }, this::render);
    }
}
