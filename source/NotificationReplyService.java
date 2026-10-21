package ru.replyai.android;

import android.app.Notification;
import android.app.RemoteInput;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/**
 * Answers messenger notifications (Telegram, WhatsApp, VK, Viber, SMS) on this phone through their
 * «Reply» action. The text goes to the VPS only for the AI answer; no account is connected to the server.
 */
public final class NotificationReplyService extends NotificationListenerService {
    static final String PREFS = "replyai_features";
    static final String ENABLED = "notify_enabled";
    static final String CHATS = "notify_chats";
    static final String LAST = "notify_last";
    static final String TRACE = "notify_trace";
    static final String ALL = "notify_all";
    static final String IGNORE = "notify_ignore";
    static final String HISTORY = "notify_history";
    static final String QUICK = "notify_quick";      // lines «слово=ответ»: a fixed reply without the AI
    static final String STOP = "notify_stop";        // words that call the owner instead of the bot
    static final String LIMIT = "notify_limit";      // AI replies per day, 0 = no limit
    static final String COUNT_DAY = "notify_count_day";
    static final String COUNT = "notify_count";
    static final String ALERTS = "zamolvi_urgent";
    private static final int HISTORY_SIZE = 100;
    private static final String OUR_PREFIX = "[AI-автоответ]"; // the server marks every AI reply
    private static final String TAG = "ZamolviNotifyReply";
    private static final long PER_CHAT_INTERVAL_MS = 20_000; // one answer per chat at a time, no reply loops
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, String> lastText = new HashMap<>();
    private final Map<String, Long> lastAt = new HashMap<>();

    @Override public void onListenerDisconnected() {
        // Vendor shells unbind the listener after killing the process; ask the system to bind it again.
        requestRebind(new android.content.ComponentName(this, NotificationReplyService.class));
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            handle(sbn);
        } catch (RuntimeException error) {
            Log.w(TAG, "Notification skipped", error);
        }
    }

    private void handle(StatusBarNotification sbn) {
        SharedPreferences p = getSharedPreferences(PREFS, 0);
        String app = Rules.app(sbn.getPackageName());
        if (app == null || !p.getBoolean("app_" + app, "telegram".equals(app))) return;
        if (!p.getBoolean(ENABLED, false)) { trace(p, "", "ответы выключены"); return; }
        Notification n = sbn.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return; // the group header, not a chat
        Bundle x = n.extras;
        if (x == null) return;
        CharSequence title = x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        if (title == null) title = x.getCharSequence(Notification.EXTRA_TITLE);
        String chat = title == null ? "" : title.toString().trim();
        if (x.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)) { trace(p, chat, "групповой чат — пропущен"); return; }
        if (chat.isEmpty()) { trace(p, "", "в уведомлении нет имени чата"); return; }
        // The «Telegram» service chat carries login codes: they must never leave the phone.
        if ("telegram".equals(app) && "Telegram".equalsIgnoreCase(chat)) { trace(p, chat, "служебный чат Telegram — не отвечаем"); return; }
        if (allowed(p.getString(IGNORE, ""), chat)) { trace(p, chat, "в игноре"); return; }
        // SMS also come from banks and shops: only people named in the list, never «everyone».
        boolean everyone = p.getBoolean(ALL, false) && !"sms".equals(app);
        if (!everyone && !allowed(p.getString(CHATS, ""), chat)) {
            trace(p, chat, "sms".equals(app) ? "SMS: отвечаем только тем, кто в списке чатов"
                    : "нет в списке чатов (впишите имя точно так)");
            return;
        }
        String text = lastIncoming(x);
        if (text == null) { trace(p, chat, "последнее сообщение — наш же автоответ"); return; }
        if (text.trim().isEmpty()) { trace(p, chat, "нет текста сообщения: включите показ текста в уведомлениях мессенджера"); return; }
        if (Rules.looksLikeCode(text)) { trace(p, chat, "похоже на код подтверждения — на сервер не отправляем"); return; }
        Notification.Action reply = replyAction(n);
        if (reply == null) { trace(p, chat, "у уведомления нет кнопки «Ответить»"); return; }
        long now = System.currentTimeMillis();
        boolean urgent = Rules.hasAny(p.getString(STOP, ""), text);
        synchronized (this) {
            if (text.equals(lastText.get(chat))) return; // the same notification updated again
            Long previous = lastAt.get(chat);
            if (!urgent && previous != null && now - previous < PER_CHAT_INTERVAL_MS) {
                trace(p, chat, "меньше 20 секунд после прошлого ответа — пропущено");
                return;
            }
            lastText.put(chat, text);
            if (!urgent) lastAt.put(chat, now);
        }
        String message = text.length() > 4000 ? text.substring(0, 4000) : text;
        if (urgent) {
            trace(p, chat, "срочное слово — ответьте сами");
            alert(sbn.getPackageName(), chat, message);
            return;
        }
        String fixed = Rules.fixedReply(p.getString(QUICK, ""), message);
        if (fixed != null) {
            worker.execute(() -> deliver(chat, OUR_PREFIX + " " + fixed, reply, false));
            return;
        }
        int limit = p.getInt(LIMIT, 0);
        if (limit > 0 && usedToday(p) >= limit) {
            trace(p, chat, "дневной лимит ответов нейросети исчерпан (" + limit + ")");
            return;
        }
        worker.execute(() -> answer(chat, message, reply));
    }

    /** The owner must answer this one: a loud notification that opens the messenger. */
    private void alert(String pkg, String chat, String text) {
        try {
            android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
            nm.createNotificationChannel(new android.app.NotificationChannel(ALERTS,
                    I18n.t(this, "Срочные сообщения"), android.app.NotificationManager.IMPORTANCE_HIGH));
            Intent open = getPackageManager().getLaunchIntentForPackage(pkg);
            Notification.Builder b = new Notification.Builder(this, ALERTS)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle(I18n.t(this, "Ответьте сами") + ": " + chat)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setAutoCancel(true);
            if (open != null) b.setContentIntent(android.app.PendingIntent.getActivity(this, 0, open,
                    android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT));
            nm.notify(chat.hashCode(), b.build());
        } catch (RuntimeException error) {
            Log.w(TAG, "Urgent alert not shown", error); // no permission to notify: the trace still says it
        }
    }

    private static int usedToday(SharedPreferences p) {
        return today().equals(p.getString(COUNT_DAY, "")) ? p.getInt(COUNT, 0) : 0;
    }

    private static String today() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new java.util.Date());
    }

    /** The newest message; null when it is our own AI reply, "" when the text is hidden. */
    private static String lastIncoming(Bundle x) {
        Parcelable[] raw = x.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (raw != null && raw.length > 0) {
            List<Notification.MessagingStyle.Message> messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(raw);
            if (messages.isEmpty()) return "";
            Notification.MessagingStyle.Message last = messages.get(messages.size() - 1);
            return own(last.getText());
        }
        return own(x.getCharSequence(Notification.EXTRA_TEXT));
    }

    // Not the sender field: some Telegram builds leave it empty for incoming personal messages.
    private static String own(CharSequence value) {
        String text = value == null ? "" : value.toString();
        return text.trim().startsWith(OUR_PREFIX) ? null : text;
    }

    private static Notification.Action replyAction(Notification n) {
        if (n.actions == null) return null;
        Notification.Action found = null;
        for (Notification.Action action : n.actions) {
            RemoteInput[] inputs = action.getRemoteInputs();
            if (inputs == null || inputs.length == 0 || action.actionIntent == null) continue;
            if (action.getSemanticAction() == Notification.Action.SEMANTIC_ACTION_REPLY) return action;
            if (found == null) found = action;
        }
        return found;
    }

    private void answer(String chat, String text, Notification.Action reply) {
        // The phone may fall asleep between the AI request and the reply.
        android.os.PowerManager.WakeLock lock = getSystemService(android.os.PowerManager.class)
                .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "zamolvi:notification-reply");
        lock.acquire(90_000);
        try {
            RemoteSettings settings = new RemoteSettings(this);
            if (settings.url().isEmpty() || settings.token().isEmpty()) {
                finish(chat, "не настроено подключение к VPS");
                return;
            }
            JSONObject data = new JSONObject().put("text", text);
            SharedPreferences p = getSharedPreferences(PREFS, 0);
            int profile = activeProfile(p);
            if (profile >= 0) { // the server before 3.3.0 would refuse these fields: only sent while a profile is on
                String style = p.getString("prof_" + profile + "_style", "-");
                if (!"-".equals(style)) data.put("style", style);
                data.put("note", p.getString("prof_" + profile + "_note", Rules.PROFILES[profile][1]));
            }
            JSONObject response = new RemoteClient(settings.url(), settings.token())
                    .call("POST", "/api/notification/answer", data);
            String answer = response.optString("answer").trim();
            if (answer.isEmpty()) throw new Exception("AI не вернул ответ");
            deliver(chat, answer, reply, true);
        } catch (Exception error) {
            Log.w(TAG, "Reply failed", error);
            finish(chat, error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        } finally {
            if (lock.isHeld()) lock.release();
        }
    }

    /** Sends the text through the «Reply» action and records it; counts toward the daily limit when the AI wrote it. */
    private void deliver(String chat, String answer, Notification.Action reply, boolean ai) {
        try {
            Bundle values = new Bundle();
            for (RemoteInput input : reply.getRemoteInputs()) values.putCharSequence(input.getResultKey(), answer);
            Intent intent = new Intent();
            RemoteInput.addResultsToIntent(reply.getRemoteInputs(), intent, values);
            reply.actionIntent.send(this, 0, intent);
            SharedPreferences p = getSharedPreferences(PREFS, 0);
            if (ai) p.edit().putString(COUNT_DAY, today()).putInt(COUNT, usedToday(p) + 1).apply();
            remember(this, chat);
            record("Ответ отправлен: «" + chat + "»" + (ai ? "" : " (быстрый ответ)"));
        } catch (Exception error) {
            Log.w(TAG, "Reply failed", error);
            finish(chat, error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }
    }

    private void finish(String chat, String reason) {
        record("Не удалось ответить «" + chat + "»: " + reason
                + (unrestricted(this) ? "" : " (работа в фоне ограничена — снимите ограничение батареи)"));
    }

    private void record(String result) {
        getSharedPreferences(PREFS, 0).edit()
                .putString(LAST, new java.text.SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
                        .format(new java.util.Date()) + " · " + result).apply();
    }

    /** Index of the profile that is on now (by hand or by its time), -1 when none. */
    static int activeProfile(SharedPreferences p) {
        int n = Rules.PROFILES.length;
        boolean[] manual = new boolean[n];
        int[] from = new int[n], to = new int[n];
        for (int i = 0; i < n; i++) {
            manual[i] = p.getBoolean("prof_" + i + "_on", false);
            from[i] = Rules.minutes(p.getString("prof_" + i + "_from", ""));
            to[i] = Rules.minutes(p.getString("prof_" + i + "_to", ""));
        }
        java.util.Calendar c = java.util.Calendar.getInstance();
        return Rules.activeProfile(manual, from, to, c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE));
    }

    /** Why the last messenger notification was not answered: shown on the screen and in the feature check. */
    private static void trace(SharedPreferences p, String chat, String reason) {
        p.edit().putString(TRACE, new java.text.SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
                .format(new java.util.Date()) + " · " + (chat.isEmpty() ? "" : "«" + chat + "»: ") + reason).apply();
    }

    /** «Кому ответил»: name → number of answers and the time of the last one, newest kept. */
    private static synchronized void remember(android.content.Context c, String chat) {
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        try {
            JSONObject all = new JSONObject(p.getString(HISTORY, "{}"));
            JSONObject row = all.optJSONObject(chat);
            all.put(chat, new JSONObject().put("count", row == null ? 1 : row.optInt("count") + 1)
                    .put("at", System.currentTimeMillis()));
            while (all.length() > HISTORY_SIZE) {
                String oldest = null;
                for (java.util.Iterator<String> i = all.keys(); i.hasNext(); ) {
                    String key = i.next();
                    if (oldest == null || all.getJSONObject(key).optLong("at") < all.getJSONObject(oldest).optLong("at")) oldest = key;
                }
                all.remove(oldest);
            }
            p.edit().putString(HISTORY, all.toString()).apply();
        } catch (org.json.JSONException error) {
            p.edit().remove(HISTORY).apply(); // a damaged list is only statistics: start it again
        }
    }

    /** Without this Android and vendor shells cut the network of a background app (Doze, HiOS). */
    static boolean unrestricted(android.content.Context c) {
        android.os.PowerManager pm = c.getSystemService(android.os.PowerManager.class);
        return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    static boolean granted(android.content.Context c) {
        android.app.NotificationManager nm = c.getSystemService(android.app.NotificationManager.class);
        return nm != null && nm.isNotificationListenerAccessGranted(
                new android.content.ComponentName(c, NotificationReplyService.class));
    }

    /** One chat name per line, compared without case; an empty list answers nobody. */
    static boolean allowed(String list, String chat) {
        String wanted = chat.toLowerCase(Locale.ROOT);
        for (String line : list.split("\n")) {
            if (!line.trim().isEmpty() && line.trim().toLowerCase(Locale.ROOT).equals(wanted)) return true;
        }
        return false;
    }
}
