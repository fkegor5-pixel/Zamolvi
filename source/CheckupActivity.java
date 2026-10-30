package ru.replyai.android;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * «Проверка функций»: why the notification, tile, widget, memory, drafts, photos, documents
 * or statistics do not work — with a fix button for each problem and a savable report.
 */
public final class CheckupActivity extends FeatureActivity {
    private static final int SAVE = 2915;
    static final String MIN_SERVER = "3.4.2";
    private LinearLayout phone;
    private LinearLayout server;
    private final ArrayList<View> ownedPhone = new ArrayList<>();
    private final ArrayList<View> ownedServer = new ArrayList<>();
    private JSONArray phoneChecks = new JSONArray();
    private JSONArray serverChecks = new JSONArray();
    private JSONObject serverRaw = new JSONObject();
    private String pendingReport;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        title("Проверка функций");
        text("Почему не работает или не включается функция — и что сделать. Нажмите «Сохранить отчёт», "
                + "чтобы отправить результат вместе с журналом диагностики.", 15);
        status = text("", 15);
        button("Проверить снова", v -> load());
        button("Сохранить отчёт", v -> save());
        text("Телефон", 20).setTypeface(null, android.graphics.Typeface.BOLD);
        phone = section();
        text("Сервер", 20).setTypeface(null, android.graphics.Typeface.BOLD);
        server = section();
        load();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!busy && phone != null) renderPhone(); // e.g. back from the notification settings
    }

    private void load() {
        renderPhone();
        execute(() -> {
            JSONObject result = new JSONObject();
            JSONObject status;
            try {
                status = request("GET", "/api/status", null);
            } catch (Exception error) {
                return result.put("error", String.valueOf(error.getMessage()));
            }
            result.put("status", status);
            try {
                result.put("features", request("GET", "/api/features", null));
            } catch (Exception error) {
                result.put("features_error", String.valueOf(error.getMessage()));
            }
            try {
                result.put("diagnostics", request("GET", "/api/diagnostics", null));
            } catch (Exception ignored) { }
            return result;
        }, this::renderServer);
    }

    // ------------------------------------------------------------------ phone

    private void renderPhone() {
        release(ownedPhone);
        phone.removeAllViews();
        phoneChecks = new JSONArray();
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            row(phone, "app", "Приложение", "ok", "Версия " + info.versionName + " (" + info.getLongVersionCode() + "), Android "
                    + Build.VERSION.RELEASE + ", " + Build.MANUFACTURER + " " + Build.MODEL, null, null);
        } catch (Exception ignored) { }

        RemoteSettings settings = new RemoteSettings(this);
        boolean connected = !settings.url().isEmpty() && !settings.token().isEmpty();
        row(phone, "vps", "Подключение к VPS", connected ? "ok" : "problem",
                connected ? "Адрес и ключ сохранены." : "Адрес VPS или ключ управления не сохранены: «1. Подключение к VPS».",
                connected ? null : "Открыть подключение", connected ? null : () -> openServerSetup(this));

        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = nm == null ? null : nm.getNotificationChannel(StatusNotification.CHANNEL);
        if (!StatusNotification.permitted(this)) {
            boolean asked = getSharedPreferences("replyai_features", 0).getBoolean("notification_permission_asked", false);
            row(phone, "notification", "Уведомление в шторке", "problem",
                    "Нет разрешения на уведомления (Android 13+)." + (asked ? " Запрос уже отклонялся — включите в настройках." : ""),
                    asked ? "Открыть настройки уведомлений" : "Разрешить уведомления",
                    asked ? () -> StatusNotification.openSettings(this) : () -> StatusNotification.requestPermission(this));
        } else if (nm == null || !nm.areNotificationsEnabled()) {
            row(phone, "notification", "Уведомление в шторке", "problem", "Уведомления " + Brand.NAME + " выключены в настройках Android.",
                    "Открыть настройки уведомлений", () -> StatusNotification.openSettings(this));
        } else if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
            row(phone, "notification", "Уведомление в шторке", "problem", "Выключен канал «Статус автоответчика».",
                    "Открыть настройки уведомлений", () -> StatusNotification.openSettings(this));
        } else if (StatusNotification.visible(this)) {
            row(phone, "notification", "Уведомление в шторке", "ok", "Показано в шторке. Если его не видно, проверьте раздел "
                    + "тихих уведомлений и запрет уведомлений для приложения в «Диспетчере телефона»." + vendorHint(), null, null);
        } else {
            row(phone, "notification", "Уведомление в шторке", "warning",
                    "Разрешено, но сейчас не показано — например, его смахнули. Нажмите, чтобы показать сейчас.",
                    "Показать уведомление", () -> { AutoReplyState.publish(this); ControlReceiver.requestUpdate(this); renderPhone(); });
        }

        if (!vendorHint().isEmpty()) {
            row(phone, "vendor", "Фоновые ограничения производителя", "warning", vendorHint().trim()
                    + " Без этого после перезагрузки или долгого сна уведомление и виджет могут не обновляться.",
                    "Открыть настройки приложения", () -> {
                        try {
                            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (RuntimeException ignored) { }
                    });
        }

        int tile = getSharedPreferences("replyai_features", 0).getInt(StatusNotification.TILE_STATE, Integer.MIN_VALUE);
        if (Build.VERSION.SDK_INT >= 33) {
            boolean added = tile == 1 || tile == 2;
            row(phone, "tile", "Плитка «Автоответчик» в быстрых настройках", added ? "ok" : "warning",
                    added ? "Добавлена." : tile == 0 ? "Вы отказались добавить плитку." : tile == Integer.MIN_VALUE
                            ? "Неизвестно, добавлена ли плитка. Нажмите кнопку — Android сам скажет или предложит добавить."
                            : "Android не дал показать запрос (код " + tile + "). Добавьте вручную: шторка → карандаш → «Автоответчик».",
                    added ? null : "Добавить плитку", added ? null : () -> StatusNotification.requestTile(this));
        } else {
            row(phone, "tile", "Плитка «Автоответчик» в быстрых настройках", "warning",
                    "Добавляется вручную: откройте шторку → карандаш (изменить) → перетащите «Автоответчик».", null, null);
        }

        int widgets = ReplyWidget.count(this);
        row(phone, "widget", "Виджет на рабочем столе", widgets > 0 ? "ok" : "off",
                widgets > 0 ? "Добавлено виджетов: " + widgets + "."
                        : "Не добавлен: долгое нажатие на рабочем столе → Виджеты → " + Brand.NAME + ".", null, null);

        android.content.SharedPreferences np = getSharedPreferences(NotificationReplyService.PREFS, 0);
        String last = np.getString(NotificationReplyService.LAST, "");
        boolean listOk = np.getBoolean(NotificationReplyService.ALL, false)
                || !np.getString(NotificationReplyService.CHATS, "").trim().isEmpty();
        boolean access = NotificationReplyService.granted(this);
        boolean notifyOn = np.getBoolean(NotificationReplyService.ENABLED, false);
        row(phone, "notification_reply", "Ответы из уведомлений",
                !notifyOn ? "off" : access && listOk && NotificationReplyService.unrestricted(this) ? "ok" : "problem",
                (!notifyOn ? "Выключено: «2. Telegram» → «Ответы из уведомлений»."
                        : !access ? "Нет доступа к уведомлениям." : !listOk ? "Список чатов пуст — отвечать некому."
                        : !NotificationReplyService.unrestricted(this)
                        ? "Батарея ограничивает приложение: в фоне ответы не уходят. Снимите ограничение на экране ответов."
                        : "Включено.")
                        + (last.isEmpty() ? "" : "\nПоследний ответ: " + last)
                        + (np.getString(NotificationReplyService.TRACE, "").isEmpty() ? ""
                                : "\nПоследнее пропущенное: " + np.getString(NotificationReplyService.TRACE, "")),
                "Открыть", () -> startActivity(new Intent(this, NotificationReplyActivity.class)));
    }

    private static final String[] AGGRESSIVE_VENDORS = {"Xiaomi", "Redmi", "POCO", "Huawei", "Honor", "OPPO", "realme",
            "OnePlus", "vivo", "iQOO", "Meizu", "Samsung", "Asus", "Lenovo", "Motorola", "Nokia", "HMD Global",
            "TECNO", "Infinix", "itel", "Blackview", "Ulefone", "Doogee", "Oukitel"};

    private static String vendorHint() {
        String maker = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        // Vendors whose battery savers are known to stop background apps and widgets.
        for (String vendor : AGGRESSIVE_VENDORS) {
            if (vendor.equalsIgnoreCase(maker)) {
                return "\nОболочка " + maker + " может ограничивать работу в фоне: разрешите автозапуск и снимите "
                        + "ограничение батареи для " + Brand.NAME + ".";
            }
        }
        return "";
    }

    // ------------------------------------------------------------------ server

    private void renderServer(JSONObject value) throws Exception {
        serverRaw = value;
        release(ownedServer);
        server.removeAllViews();
        serverChecks = new JSONArray();
        if (value.has("error")) {
            String message = value.getString("error");
            row(server, "server", "Связь с сервером", "problem", "VPS не отвечает: " + message
                    + " Проверьте, что сервер запущен и адрес верный.", "Открыть подключение", () -> openServerSetup(this));
            status.setText("Сервер недоступен.");
            return;
        }
        JSONObject s = value.getJSONObject("status");
        String version = s.optString("version", "?");
        boolean fresh = newer(version, MIN_SERVER) && value.has("features");
        row(server, "server", "Версия сервера", fresh ? "ok" : "problem", fresh ? "Сервер " + version + "."
                : "Сервер " + version + " — устарел. Память, черновики, фото и документы, подробная статистика и эта проверка "
                + "требуют сервер " + MIN_SERVER + " или новее. Обновление не трогает настройки и вход в Telegram.",
                fresh ? null : "Обновить сервер", fresh ? null : () -> openServerSetup(this));
        if (!fresh) {
            status.setText("Главная причина: сервер на VPS устарел. Нажмите «Обновить сервер».");
            return;
        }
        JSONObject f = value.getJSONObject("features");
        String[][] names = {{"autoresponder", "Автоответчик"}, {"memory", "Память собеседников"}, {"approval", "Черновики (согласование)"},
                {"photos", "Чтение фото"}, {"documents", "Чтение документов"}, {"pricing", "Стоимость в статистике"}};
        int problems = 0;
        for (String[] name : names) {
            JSONObject item = f.optJSONObject(name[0]);
            if (item == null) continue;
            String state = item.optString("status");
            String reason = item.optString("reason");
            if (item.has("last_problem")) reason += "\nПоследняя проблема (" + time(item.optDouble("last_problem_at", 0)) + "): " + item.optString("last_problem");
            if ("problem".equals(state)) problems++;
            row(server, name[0], name[1], state, reason, null, null);
        }
        status.setText(problems == 0 ? "Серьёзных проблем не найдено." : "Найдено проблем: " + problems + ". Причины — ниже.");
    }

    static boolean newer(String version, String minimum) {
        String[] a = version.split("\\."), b = minimum.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x, y;
            try { x = i < a.length ? Integer.parseInt(a[i].replaceAll("[^0-9].*", "")) : 0; } catch (NumberFormatException e) { x = 0; }
            try { y = i < b.length ? Integer.parseInt(b[i]) : 0; } catch (NumberFormatException e) { y = 0; }
            if (x != y) return x > y;
        }
        return true;
    }

    // ------------------------------------------------------------------ rows and report

    private void row(LinearLayout parent, String id, String title, String state, String reason, String fix, Runnable action) {
        String mark = "ok".equals(state) ? "✅ " : "problem".equals(state) ? "❌ " : "off".equals(state) ? "⏸ " : "⚠️ ";
        LinearLayout card = card(parent);
        heading(card, mark + title);
        label(card, reason, 14, false).setTextIsSelectable(true);
        boolean onPhone = parent == phone;
        if (fix != null) action(card, fix, v -> action.run(), onPhone ? ownedPhone : ownedServer);
        try {
            (onPhone ? phoneChecks : serverChecks).put(new JSONObject().put("id", id).put("title", title).put("status", state).put("reason", reason));
        } catch (Exception ignored) { }
    }

    private void save() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, Brand.NAME + "-features-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".json");
        try {
            pendingReport = report().toString(2);
            startActivityForResult(intent, SAVE);
        } catch (Exception error) {
            status.setText("Не удалось подготовить отчёт: " + error.getMessage());
        }
    }

    private JSONObject report() throws Exception {
        JSONObject server = new JSONObject();
        if (serverRaw.has("status")) server.put("version", serverRaw.getJSONObject("status").optString("version"));
        if (serverRaw.has("features")) server.put("features", serverRaw.getJSONObject("features"));
        if (serverRaw.has("features_error")) server.put("features_error", serverRaw.getString("features_error"));
        if (serverRaw.has("error")) server.put("error", serverRaw.getString("error"));
        JSONObject result = new JSONObject()
                .put("schema", "replyai.feature-check.v1")
                .put("generated_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(new Date()))
                .put("phone_checks", phoneChecks).put("server_checks", serverChecks).put("server", server);
        try {
            // The usual diagnostics journal of the app, so one file has everything.
            result.put("diagnostics", DiagnosticLog.get().report(serverRaw.optJSONObject("diagnostics")));
        } catch (Throwable error) {
            result.put("diagnostics_error", error.getClass().getSimpleName());
        }
        return result;
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != SAVE || result != Activity.RESULT_OK || data == null || data.getData() == null || pendingReport == null) return;
        Uri uri = data.getData();
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new java.io.IOException("no stream");
            out.write(pendingReport.getBytes(StandardCharsets.UTF_8));
            status.setText("Отчёт сохранён.");
        } catch (Exception error) {
            status.setText("Не удалось сохранить отчёт.");
        }
    }
}
