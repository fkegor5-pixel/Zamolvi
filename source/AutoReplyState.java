package ru.replyai.android;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/**
 * One cached auto-reply state shared by the home-screen widget, the status notification and
 * the Quick Settings tile. All VPS requests go through one background thread.
 */
final class AutoReplyState {
    private static final String TAG = "ZamolviState";
    private static final String PREFS = "replyai_status";
    private static final long FRESH_MS = 60 * 1000L;

    /** Silent update: alarm, boot, leaving the app. */
    static final int UPDATE = 0;
    /** Update with visible progress: «Обновить» button. */
    static final int REFRESH = 1;
    static final int ENABLE = 2;
    static final int DISABLE = 3;

    private static final ExecutorService network = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean updating = new AtomicBoolean();
    private static volatile String progress;

    private AutoReplyState() { }

    static final class Snapshot {
        boolean configured;   // VPS address and key saved in the app
        boolean known;        // at least one successful answer from the VPS
        boolean offline;      // the last request failed; values below are the last known ones
        boolean authorized;
        boolean connected;
        boolean enabled;
        boolean quiet;
        int sent;             // all time
        int today = -1;       // today, -1 if the server did not report it
        int queued;
        int awaiting;         // drafts waiting for manual approval
        boolean serverOld;    // the VPS runs a server older than this app needs
        String time;          // HH:mm of the last successful update

        /** The VPS answered and Telegram is logged in: start/stop make sense. */
        boolean ready() { return configured && known && authorized; }

        boolean running() { return ready() && connected && enabled; }

        /** Same wording as the status card in the app. */
        String status() {
            if (!configured) return "VPS не подключён";
            if (!known) return Brand.NAME;
            if (!authorized) return "Telegram не подключён";
            if (!connected) return "Telegram переподключается";
            if (!enabled) return "Автоответы остановлены";
            return quiet ? "Тихие часы · автоответы на паузе" : "Автоответы включены";
        }

        String counters() {
            if (!configured) return "Откройте приложение → «Подключение к VPS»";
            if (!known) return "Нажмите «Обновить»";
            String line = today >= 0
                    ? "Сегодня отправлено: " + today + " · Всего: " + sent
                    : "Отправлено: " + sent;
            if (queued > 0) line += " · В очереди: " + queued;
            return awaiting > 0 ? "Ждут согласования: " + awaiting + "\n" + line : line;
        }

        /** Short form for the Quick Settings tile subtitle. */
        String shortCounters() {
            if (!configured || !known) return "Нет данных";
            if (awaiting > 0) return "Черновики: " + awaiting;
            return today >= 0 ? "Сегодня: " + today : "Всего: " + sent;
        }

        String footer() {
            if (serverOld) return "Сервер на VPS устарел — откройте " + Brand.NAME + " → «15. Проверка функций»";
            if (!configured || time == null) return "";
            return offline ? "Нет связи с VPS · данные на " + time : "Обновлено в " + time;
        }
    }

    static Snapshot read(Context c) {
        SharedPreferences p = prefs(c);
        Snapshot s = new Snapshot();
        s.configured = p.getBoolean("configured", true);
        s.known = p.getBoolean("known", false);
        s.offline = p.getBoolean("offline", false);
        s.authorized = p.getBoolean("authorized", false);
        s.connected = p.getBoolean("connected", false);
        s.enabled = p.getBoolean("enabled", false);
        s.quiet = p.getBoolean("quiet", false);
        s.sent = p.getInt("sent", 0);
        s.today = p.getInt("today", -1);
        s.queued = p.getInt("queued", 0);
        s.awaiting = p.getInt("awaiting", 0);
        s.serverOld = !CheckupActivity.newer(p.getString("server_version", CheckupActivity.MIN_SERVER), CheckupActivity.MIN_SERVER);
        s.time = p.getString("time", null);
        return s;
    }

    /** «Включаю…» etc. while a command is running, otherwise null. */
    static String progress() { return progress; }

    static boolean isStale(Context c) {
        long at = prefs(c).getLong("at", 0);
        long now = System.currentTimeMillis();
        return at <= 0 || now < at || now - at > FRESH_MS;
    }

    /**
     * Runs a command in the background, then redraws every surface.
     * @param receiver keeps the broadcast alive until the request ends; null from the tile.
     */
    static void run(BroadcastReceiver receiver, Context context, int command) {
        Context app = context.getApplicationContext();
        boolean update = command == UPDATE || command == REFRESH;
        // Several silent/refresh requests at once collapse into one.
        if (update && !updating.compareAndSet(false, true)) return;
        if (command == REFRESH) progress = "Обновляю…";
        else if (command == ENABLE) progress = "Включаю…";
        else if (command == DISABLE) progress = "Останавливаю…";
        if (progress != null) publish(app);
        BroadcastReceiver.PendingResult pending = receiver == null ? null : receiver.goAsync();
        network.execute(() -> {
            try {
                fetch(app, command);
            } finally {
                progress = null;
                if (update) updating.set(false);
                try {
                    publish(app);
                } catch (RuntimeException error) {
                    Log.w(TAG, "Cannot redraw", error);
                }
                if (pending != null) pending.finish();
            }
        });
    }

    private static void fetch(Context app, int command) {
        SharedPreferences p = prefs(app);
        RemoteSettings settings;
        try {
            settings = new RemoteSettings(app);
            if (settings.url().isEmpty() || settings.token().isEmpty()) {
                p.edit().putBoolean("configured", false).putBoolean("offline", false).apply();
                return;
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Cannot read settings", error);
            return;
        }
        try {
            RemoteClient client = new RemoteClient(settings.url(), settings.token());
            JSONObject status = command == ENABLE || command == DISABLE
                    ? client.call("POST", "/api/control", new JSONObject().put("enabled", command == ENABLE))
                    : client.call("GET", "/api/status", null);
            int today = -1;
            try {
                JSONObject day = client.call("GET", "/api/stats", null).optJSONObject("today");
                if (day != null) today = day.optInt("replies", -1);
            } catch (Exception ignored) {
                // Older servers have no statistics: the total is still shown.
            }
            p.edit()
                    .putBoolean("configured", true)
                    .putBoolean("known", true)
                    .putBoolean("offline", false)
                    .putBoolean("authorized", status.optBoolean("authorized"))
                    .putBoolean("connected", status.optBoolean("connected"))
                    .putBoolean("enabled", status.optBoolean("enabled"))
                    .putBoolean("quiet", status.optBoolean("quiet_active"))
                    .putInt("sent", status.optInt("sent"))
                    .putInt("today", today)
                    .putInt("queued", status.optInt("queued"))
                    .putInt("awaiting", status.optInt("awaiting"))
                    .putString("server_version", status.optString("version", "0"))
                    .putString("time", new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()))
                    .putLong("at", System.currentTimeMillis())
                    .apply();
        } catch (Exception error) {
            Log.w(TAG, "VPS request failed", error);
            p.edit().putBoolean("configured", true).putBoolean("offline", true).apply();
        }
    }

    /** Redraws the widget, the notification and the tile from the cache. */
    static void publish(Context c) {
        Context app = c.getApplicationContext();
        Snapshot s = read(app);
        String now = progress;
        ReplyWidget.render(app, s, now);
        StatusNotification.show(app, s, now);
        ReplyTileService.requestUpdate(app);
        schedule(app);
    }

    /**
     * Updates every ~15 minutes while the widget or the notification is in use.
     * Not a wakeup alarm: a sleeping phone is not woken up just for this.
     */
    private static void schedule(Context c) {
        AlarmManager alarms = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        PendingIntent tick = ControlReceiver.pending(c, ControlReceiver.ACTION_UPDATE, 10);
        if (ReplyWidget.count(c) > 0 || StatusNotification.allowed(c)) {
            alarms.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                    AlarmManager.INTERVAL_FIFTEEN_MINUTES, tick);
        } else {
            alarms.cancel(tick);
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int res(Context c, String name, String type) {
        return c.getResources().getIdentifier(name, type, c.getPackageName());
    }
}
