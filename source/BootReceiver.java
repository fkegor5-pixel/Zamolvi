package ru.replyai.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Notifications and alarms are cleared by a reboot or an app update: restore them.
 * Both broadcasts are protected, only the system can send them.
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            AutoReplyState.publish(context); // cached state right away
            AutoReplyState.run(this, context, AutoReplyState.UPDATE);
        }
    }
}
