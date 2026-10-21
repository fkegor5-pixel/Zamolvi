package ru.replyai.android;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Buttons of the widget and the notification, plus the periodic update.
 * Not exported: only this app's PendingIntents can switch auto-replies on or off.
 */
public final class ControlReceiver extends BroadcastReceiver {
    static final String ACTION_UPDATE = "ru.replyai.android.control.UPDATE";
    static final String ACTION_REFRESH = "ru.replyai.android.control.REFRESH";
    static final String ACTION_ENABLE = "ru.replyai.android.control.ENABLE";
    static final String ACTION_DISABLE = "ru.replyai.android.control.DISABLE";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_UPDATE.equals(action)) AutoReplyState.run(this, context, AutoReplyState.UPDATE);
        else if (ACTION_REFRESH.equals(action)) AutoReplyState.run(this, context, AutoReplyState.REFRESH);
        else if (ACTION_ENABLE.equals(action)) AutoReplyState.run(this, context, AutoReplyState.ENABLE);
        else if (ACTION_DISABLE.equals(action)) AutoReplyState.run(this, context, AutoReplyState.DISABLE);
    }

    static PendingIntent pending(Context c, String action, int code) {
        Intent intent = new Intent(c, ControlReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(c, code, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Silent update, e.g. after leaving the app. */
    static void requestUpdate(Context context) {
        Context app = context.getApplicationContext();
        app.sendBroadcast(new Intent(app, ControlReceiver.class).setAction(ACTION_UPDATE));
    }
}
