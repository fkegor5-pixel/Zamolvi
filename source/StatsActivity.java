package ru.replyai.android;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** «Подробная статистика»: AI costs by model and purpose, per chat, response time, errors, approval. */
public final class StatsActivity extends FeatureActivity {
    private static final int[] PERIODS = {1, 7, 30, 0};
    private static final String[] PERIOD_NAMES = {"Сегодня", "7 дней", "30 дней", "Всё время"};
    private int period = 7;
    private LinearLayout summary;
    private LinearLayout models;
    private LinearLayout purposes;
    private LinearLayout chats;
    private LinearLayout prices;
    private EditText currency;
    private final ArrayList<View> ownedPrices = new ArrayList<>();
    private final Map<String, EditText[]> priceFields = new LinkedHashMap<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        title("Подробная статистика");
        text("Сколько сообщений получено и отправлено, сколько стоили запросы к AI, как быстро приходят ответы и "
                + "где возникали ошибки — по моделям, задачам и чатам. Выберите период.", 15);
        for (int i = 0; i < PERIODS.length; i++) {
            int value = PERIODS[i];
            button(PERIOD_NAMES[i], v -> { period = value; load(); });
        }
        status = text("", 15);
        summary = section();
        text("Расходы по моделям", 20).setTypeface(null, android.graphics.Typeface.BOLD);
        models = section();
        text("На что потрачено", 20).setTypeface(null, android.graphics.Typeface.BOLD);
        purposes = section();
        text("По чатам (за всё время)", 20).setTypeface(null, android.graphics.Typeface.BOLD);
        chats = section();
        text("Цены моделей", 20).setTypeface(null, android.graphics.Typeface.BOLD);
        text("Укажите цены вашего тарифа: за 1 млн входных и выходных токенов, для распознавания речи — за час аудио. "
                + "Стоимость пересчитывается по текущим ценам. Токены сервер берёт из ответов провайдера.", 14);
        LinearLayout currencyBox = section();
        label(currencyBox, "Валюта (например ₽ или $)", 13, true);
        currency = field(currencyBox, "₽", "", InputType.TYPE_CLASS_TEXT);
        prices = section();
        button("Сохранить цены", v -> savePrices());
        load();
    }

    private void load() {
        execute(() -> new JSONObject()
                .put("stats", stats())
                .put("pricing", request("GET", "/api/pricing", null)), value -> {
            renderStats(value.getJSONObject("stats"));
            renderPrices(value.getJSONObject("pricing"));
        });
    }

    private void savePrices() {
        JSONObject list = new JSONObject();
        try {
            for (Map.Entry<String, EditText[]> entry : priceFields.entrySet()) {
                EditText[] f = entry.getValue();
                JSONObject row = new JSONObject().put("input", number(f[0])).put("output", number(f[1]));
                if (f[2] != null) row.put("audio_hour", number(f[2]));
                list.put(entry.getKey(), row);
            }
        } catch (Exception e) {
            status.setText("Цены — это числа, например 0.15 или 12,5.");
            return;
        }
        String symbol = currency.getText().toString().trim();
        execute(() -> {
            request("PUT", "/api/pricing", new JSONObject().put("currency", symbol).put("models", list));
            return new JSONObject().put("stats", stats())
                    .put("pricing", request("GET", "/api/pricing", null));
        }, value -> {
            renderStats(value.getJSONObject("stats"));
            renderPrices(value.getJSONObject("pricing"));
            status.setText("Цены сохранены.");
        });
    }

    private JSONObject stats() throws Exception {
        return request("POST", "/api/stats/extended", new JSONObject().put("period", period));
    }

    private static double number(EditText field) {
        String text = field.getText().toString().trim().replace(',', '.');
        return text.isEmpty() ? 0 : Double.parseDouble(text);
    }

    private void renderStats(JSONObject s) throws Exception {
        String unit = s.optString("currency");
        String periodName = PERIOD_NAMES[indexOf(s.optInt("period_days", period))];
        JSONObject c = s.getJSONObject("counters");
        JSONObject cost = s.getJSONObject("cost");
        JSONObject a = s.getJSONObject("approval");
        JSONObject e = s.getJSONObject("error_kinds");
        status.setText("Период: " + periodName);

        summary.removeAllViews();
        LinearLayout money = card(summary);
        heading(money, "Стоимость AI: " + money(cost.optDouble("total", 0), unit)
                + (cost.optBoolean("complete") ? "" : " *"));
        if (!cost.optBoolean("complete")) {
            label(money, "* Для части моделей не указаны цены — их расход не посчитан. Заполните цены ниже.", 12, true);
        }
        LinearLayout replies = card(summary);
        heading(replies, "Ответы");
        label(replies, "Получено сообщений: " + c.optInt("received") + "\nОтправлено ответов: " + c.optInt("replies")
                + "\nСреднее время автоответа: " + seconds(s.optLong("average_auto_reply_ms"))
                + "\nСреднее время всех ответов: " + seconds(s.optLong("average_reply_ms"))
                + "\nСреднее время генерации AI: " + seconds(s.optLong("average_ai_ms"))
                + "\nФото: " + c.optInt("photos") + " · Документы: " + c.optInt("documents") + " · Голосовые: " + c.optInt("voice")
                + "\nОбновлений памяти: " + c.optInt("memory_updates"), 15, false);
        LinearLayout approval = card(summary);
        heading(approval, "Ручное согласование");
        double percent = a.optDouble("approved_percent", Double.NaN);
        label(approval, "Отправлено после согласования: " + (a.isNull("approved_percent") ? "—" : pct(percent))
                + "\nС согласованием: " + a.optInt("sent_approved") + " (исправлено: " + a.optInt("edited") + ")"
                + " · Автоматически: " + a.optInt("sent_auto")
                + "\nОтклонено: " + a.optInt("rejected") + " · Истекло: " + a.optInt("expired"), 15, false);
        LinearLayout errors = card(summary);
        heading(errors, "Ошибки: " + s.optInt("errors"));
        label(errors, "AI: " + e.optInt("ai") + " · Telegram: " + e.optInt("telegram") + " · Голосовые: " + e.optInt("voice")
                + "\nФото и документы: " + e.optInt("media") + " · Память: " + e.optInt("memory"), 15, false);

        models.removeAllViews();
        JSONArray rows = s.getJSONArray("models");
        if (rows.length() == 0) label(models, "За этот период запросов к AI не было.", 14, true);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject m = rows.getJSONObject(i);
            LinearLayout card = card(models);
            heading(card, m.getString("model"));
            String audio = m.optLong("audio_seconds") > 0 ? "\nАудио: " + minutes(m.optLong("audio_seconds")) : "";
            label(card, "Запросов: " + m.optInt("calls") + " · Ошибок: " + m.optInt("errors")
                    + "\nТокены: " + tokens(m.optLong("input_tokens")) + " вход · " + tokens(m.optLong("output_tokens")) + " выход"
                    + audio + "\nСреднее время: " + seconds(m.optLong("average_ms"))
                    + "\nСтоимость: " + (m.isNull("cost") ? "цена не указана" : money(m.optDouble("cost"), unit)), 14, false);
        }

        purposes.removeAllViews();
        JSONArray kinds = s.getJSONArray("purposes");
        if (kinds.length() == 0) label(purposes, "Нет данных за период.", 14, true);
        for (int i = 0; i < kinds.length(); i++) {
            JSONObject p = kinds.getJSONObject(i);
            label(card(purposes), purpose(p.getString("purpose")) + ": " + p.optInt("calls") + " запр. · "
                    + tokens(p.optLong("input_tokens") + p.optLong("output_tokens")) + " ток. · "
                    + (p.isNull("cost") ? "цена не указана" : money(p.optDouble("cost"), unit)), 14, false);
        }

        chats.removeAllViews();
        JSONArray people = s.getJSONArray("chats");
        if (people.length() == 0) label(chats, "Нет данных по выбранным чатам.", 14, true);
        for (int i = 0; i < people.length(); i++) {
            JSONObject p = people.getJSONObject(i);
            LinearLayout card = card(chats);
            heading(card, p.optString("name"));
            label(card, "Получено: " + p.optInt("received") + " · Ответов: " + p.optInt("replies") + " · Ошибок: " + p.optInt("errors")
                    + "\nСреднее время автоответа: " + seconds(p.optLong("average_auto_reply_ms"))
                    + "\nAI-запросов: " + p.optInt("ai_calls") + " · Токены: " + tokens(p.optLong("input_tokens") + p.optLong("output_tokens"))
                    + "\nПосле согласования: " + (p.isNull("approved_percent") ? "—" : pct(p.optDouble("approved_percent")))
                    + " · Отклонено: " + p.optInt("rejected")
                    + "\nФото: " + p.optInt("photos") + " · Документы: " + p.optInt("documents"), 14, false);
        }
    }

    private void renderPrices(JSONObject pricing) throws Exception {
        release(ownedPrices);
        priceFields.clear();
        prices.removeAllViews();
        if (currency.getText().length() == 0) currency.setText(pricing.optString("currency"));
        JSONObject saved = pricing.optJSONObject("models");
        JSONArray known = pricing.getJSONArray("known");
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        for (int i = 0; i < known.length(); i++) keys.add(known.getString(i));
        if (saved != null) for (Iterator<String> it = saved.keys(); it.hasNext(); ) keys.add(it.next());
        for (String key : keys) {
            JSONObject price = saved == null ? null : saved.optJSONObject(key);
            LinearLayout card = card(prices);
            heading(card, key);
            boolean audio = key.contains("whisper");
            EditText input = null, output = null, hour = null;
            if (!audio) {
                input = priceField(card, "Вход, за 1 млн токенов", price, "input");
                output = priceField(card, "Выход, за 1 млн токенов", price, "output");
            } else {
                hour = priceField(card, "За час аудио", price, "audio_hour");
            }
            priceFields.put(key, new EditText[] {
                    input != null ? input : hidden(), output != null ? output : hidden(), hour });
        }
    }

    private EditText priceField(LinearLayout card, String hint, JSONObject price, String name) {
        label(card, hint, 12, true);
        double value = price == null ? 0 : price.optDouble(name, 0);
        EditText view = field(card, "0", value > 0 ? trim(value) : "",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        ownedPrices.add(view);
        return view;
    }

    private EditText hidden() {
        EditText view = new EditText(this);
        view.setText("0");
        return view;
    }

    private static int indexOf(int days) {
        for (int i = 0; i < PERIODS.length; i++) if (PERIODS[i] == days) return i;
        return 1;
    }

    private static String purpose(String key) {
        switch (key) {
            case "reply": return "Ответы";
            case "memory": return "Память";
            case "vision": return "Фото и документы";
            case "voice": return "Голосовые";
            case "notification": return "Ответы из уведомлений";
            case "test": return "Тест AI";
            default: return key;
        }
    }

    private static String money(double value, String unit) {
        String text = value == 0 ? "0" : value < 0.01 ? String.format(Locale.US, "%.4f", value) : String.format(Locale.US, "%.2f", value);
        return unit.isEmpty() ? text : text + " " + unit;
    }

    private static String trim(double value) {
        String text = String.format(Locale.US, "%.6f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String tokens(long value) {
        return String.format(Locale.forLanguageTag("ru"), "%,d", value);
    }

    private static String seconds(long ms) {
        return ms <= 0 ? "—" : String.format(Locale.forLanguageTag("ru"), "%.1f с", ms / 1000.0);
    }

    private static String minutes(long seconds) {
        return String.format(Locale.forLanguageTag("ru"), "%.1f мин", seconds / 60.0);
    }

    private static String pct(double value) {
        return String.format(Locale.forLanguageTag("ru"), "%.1f%%", value);
    }
}
