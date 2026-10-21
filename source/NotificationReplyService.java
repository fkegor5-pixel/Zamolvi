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
 * Answers Telegram notifications on this phone through their «Reply» action. The text goes to the
 * VPS only for the AI answer; the Telegram account is never connected to the server.
 */
public final class NotificationReplyService extends NotificationListenerService {
    static final String PREFS = "replyai_features";
    static final String ENABLED = "notify_enabled";
    static final String CHATS = "notify_chats";
    static final String LAST = "notify_last";
    private static final String TAG = "ZamolviNotifyReply";
    private static final String[] TELEGRAM = {"org.telegram.messenger", "org.telegram.messenger.web",
            "org.telegram.messenger.beta"};
    private static final long PER_CHAT_INTERVAL_MS = 20_000; // one answer per chat at a time, no reply loops
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, String> lastText = new HashMap<>();
    private final Map<String, Long> lastAt = new HashMap<>();

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            handle(sbn);
        } catch (RuntimeException error) {
            Log.w(TAG, "Notification skipped", error);
        }
    }

    private void handle(StatusBarNotification sbn) {
        SharedPreferences p = getSharedPreferences(PREFS, 0);
        if (!p.getBoolean(ENABLED, false) || !isTelegram(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        Bundle x = n.extras;
        if (x == null || x.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)) return; // personal chats only
        CharSequence title = x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        if (title == null) title = x.getCharSequence(Notification.EXTRA_TITLE);
        String chat = title == null ? "" : title.toString().trim();
        if (chat.isEmpty() || !allowed(p.getString(CHATS, ""), chat)) return;
        String text = lastIncoming(x);
        if (text == null || text.trim().isEmpty()) return;
        Notification.Action reply = replyAction(n);
        if (reply == null) return;
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (text.equals(lastText.get(chat))) return; // the same notification updated again
            Long previous = lastAt.get(chat);
            if (previous != null && now - previous < PER_CHAT_INTERVAL_MS) return;
            lastText.put(chat, text);
            lastAt.put(chat, now);
        }
        String message = text.length() > 4000 ? text.substring(0, 4000) : text;
        worker.execute(() -> answer(chat, message, reply));
    }

    /** The newest message, or null when it is the owner's own (for example our reply). */
    private static String lastIncoming(Bundle x) {
        Parcelable[] raw = x.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (raw != null && raw.length > 0) {
            List<Notification.MessagingStyle.Message> messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(raw);
            if (messages.isEmpty()) return null;
            Notification.MessagingStyle.Message last = messages.get(messages.size() - 1);
            if (last.getSenderPerson() == null) return null; // MessagingStyle: no sender means the device owner
            return last.getText() == null ? null : last.getText().toString();
        }
        CharSequence text = x.getCharSequence(Notification.EXTRA_TEXT);
        return text == null ? null : text.toString();
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
        String result;
        try {
            RemoteSettings settings = new RemoteSettings(this);
            if (settings.url().isEmpty() || settings.token().isEmpty()) {
                throw new Exception("не настроено подключение к VPS");
            }
            JSONObject response = new RemoteClient(settings.url(), settings.token())
                    .call("POST", "/api/notification/answer", new JSONObject().put("text", text));
            String answer = response.optString("answer").trim();
            if (answer.isEmpty()) throw new Exception("AI не вернул ответ");
            Bundle values = new Bundle();
            for (RemoteInput input : reply.getRemoteInputs()) values.putCharSequence(input.getResultKey(), answer);
            Intent intent = new Intent();
            RemoteInput.addResultsToIntent(reply.getRemoteInputs(), intent, values);
            reply.actionIntent.send(this, 0, intent);
            result = "Ответ отправлен: «" + chat + "»";
        } catch (Exception error) {
            Log.w(TAG, "Reply failed", error);
            String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            result = "Не удалось ответить «" + chat + "»: " + reason;
        }
        getSharedPreferences(PREFS, 0).edit()
                .putString(LAST, new java.text.SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
                        .format(new java.util.Date()) + " · " + result).apply();
    }

    static boolean isTelegram(String pkg) {
        for (String name : TELEGRAM) if (name.equals(pkg)) return true;
        return false;
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
