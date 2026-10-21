package ru.replyai.android;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

/**
 * Quick Settings tile «Автоответчик»: a tap switches auto-replies on or off,
 * the subtitle shows how many messages were sent today.
 */
public final class ReplyTileService extends TileService {
    private static final String TAG = "ZamolviTile";
    private static final Handler main = new Handler(Looper.getMainLooper());
    /** The tile currently visible in the open shade, if any. */
    private static volatile ReplyTileService listening;

    @Override
    public void onStartListening() {
        super.onStartListening();
        listening = this;
        render();
        // The shade was opened: refresh if the cached data is older than a minute.
        if (AutoReplyState.progress() == null && AutoReplyState.isStale(this)) {
            AutoReplyState.run(null, this, AutoReplyState.UPDATE);
        }
    }

    @Override
    public void onStopListening() {
        if (listening == this) listening = null;
        super.onStopListening();
    }

    @Override
    public void onDestroy() {
        if (listening == this) listening = null;
        super.onDestroy();
    }

    @Override
    public void onClick() {
        super.onClick();
        // On the lock screen the phone must be unlocked before auto-replies change.
        if (isLocked()) unlockAndRun(this::toggle);
        else toggle();
    }

    private void toggle() {
        if (AutoReplyState.progress() != null) return; // a command is already running
        AutoReplyState.Snapshot s = AutoReplyState.read(this);
        if (!s.ready()) {
            openApp(); // VPS or Telegram is not set up: the app explains what to do
            return;
        }
        AutoReplyState.run(null, this, s.enabled ? AutoReplyState.DISABLE : AutoReplyState.ENABLE);
        render();
    }

    private void render() {
        Tile tile = getQsTile();
        if (tile == null) return;
        AutoReplyState.Snapshot s = AutoReplyState.read(this);
        String progress = AutoReplyState.progress();
        tile.setLabel("Автоответчик");
        tile.setIcon(Icon.createWithResource(this, StatusNotification.icon(this)));
        tile.setState(s.ready() && s.enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        String subtitle = progress != null ? progress
                : !s.configured ? "Нет VPS"
                : s.known && !s.authorized ? "Нет Telegram"
                : s.offline ? "Нет связи"
                : s.shortCounters();
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(subtitle);
        tile.setContentDescription("Автоответчик. " + s.status() + ". " + s.counters());
        tile.updateTile();
    }

    @SuppressWarnings("deprecation")
    private void openApp() {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                android.app.PendingIntent open = StatusNotification.openApp(this);
                if (open != null) startActivityAndCollapse(open);
            } else {
                Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
                if (open != null) startActivityAndCollapse(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Cannot open app", error);
        }
    }

    /** Redraws the tile: directly while the shade is open, otherwise via the system. */
    static void requestUpdate(Context c) {
        ReplyTileService live = listening;
        if (live != null) {
            main.post(() -> { if (listening == live) live.render(); });
            return;
        }
        try {
            TileService.requestListeningState(c, new ComponentName(c, ReplyTileService.class));
        } catch (RuntimeException ignored) {
            // Not added to Quick Settings, or not allowed right now.
        }
    }
}
