package ru.replyai.android;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;
import android.widget.RemoteViews;

/**
 * Home-screen widget: auto-reply status, messages sent, «Включить», «Стоп», «↻».
 * Data and commands come from {@link AutoReplyState}; buttons go to the non-exported
 * {@link ControlReceiver}, so other apps cannot switch auto-replies on or off.
 */
public final class ReplyWidget extends AppWidgetProvider {
    private static final String TAG = "ZamolviWidget";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        render(context, AutoReplyState.read(context), AutoReplyState.progress()); // cached state immediately
        AutoReplyState.run(this, context, AutoReplyState.UPDATE);
    }

    static int count(Context c) { return ids(c).length; }

    static void render(Context c, AutoReplyState.Snapshot s, String progress) {
        int[] ids = ids(c);
        if (ids.length == 0) return;
        int layout = AutoReplyState.res(c, "replyai_widget", "layout");
        if (layout == 0) return;
        try {
            String footer = s.footer();
            String detail = progress != null ? progress
                    : footer.isEmpty() ? s.counters() : s.counters() + "\n" + footer;

            RemoteViews views = new RemoteViews(c.getPackageName(), layout);
            views.setTextViewText(id(c, "widget_status"), s.status());
            views.setTextViewText(id(c, "widget_detail"), detail);
            views.setImageViewResource(id(c, "widget_dot"),
                    AutoReplyState.res(c, s.running() ? "replyai_widget_dot_on" : "replyai_widget_dot_off", "drawable"));
            PendingIntent open = s.awaiting > 0 ? StatusNotification.openApprovals(c) : StatusNotification.openApp(c);
            if (open != null) views.setOnClickPendingIntent(id(c, "widget_root"), open);
            views.setOnClickPendingIntent(id(c, "widget_enable"), ControlReceiver.pending(c, ControlReceiver.ACTION_ENABLE, 1));
            views.setOnClickPendingIntent(id(c, "widget_disable"), ControlReceiver.pending(c, ControlReceiver.ACTION_DISABLE, 2));
            views.setOnClickPendingIntent(id(c, "widget_refresh"), ControlReceiver.pending(c, ControlReceiver.ACTION_REFRESH, 3));
            AppWidgetManager.getInstance(c).updateAppWidget(ids, views);
        } catch (RuntimeException error) {
            Log.w(TAG, "Cannot render widget", error);
        }
    }

    private static int[] ids(Context c) {
        try {
            return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, ReplyWidget.class));
        } catch (RuntimeException error) {
            return new int[0];
        }
    }

    private static int id(Context c, String name) { return AutoReplyState.res(c, name, "id"); }
}
