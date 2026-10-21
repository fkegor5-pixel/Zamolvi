package ru.replyai.android;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/** Registers UI additions while keeping the supplied application DEX intact. */
public final class FeatureInitProvider extends ContentProvider {
    @Override public boolean onCreate() {
        Application app = (Application) getContext().getApplicationContext();
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle state) {
                // After onCreate: the screen has set its content and window features by then.
                main.post(() -> { if (!a.isFinishing() && !a.isDestroyed()) Brand.attach(a); });
                if (isMain(a)) HomeFeatures.attach(a);
                else if ("ru.replyai.android.DiagnosticsActivity".equals(a.getClass().getName())) addCheckupLink(a);
            }
            @Override public void onActivityDestroyed(Activity a) {
                Brand.detach(a);
                if (isMain(a)) HomeFeatures.detach(a);
            }
            @Override public void onActivityStarted(Activity a) { }
            @Override public void onActivityResumed(Activity a) {
                if (!isMain(a)) return;
                // Android 13+: one-time request; after it is granted the notification appears at once.
                StatusNotification.askOnce(a);
                AutoReplyState.publish(a);
            }
            @Override public void onActivityPaused(Activity a) { }
            @Override public void onActivityStopped(Activity a) {
                // Auto-replies may have been switched in the app: bring the shade and widget up to date.
                if (isMain(a) && !a.isChangingConfigurations()) ControlReceiver.requestUpdate(a);
            }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle state) { }
        });
        return true;
    }
    /**
     * The original diagnostics report cannot carry the state of the added features, so its screen
     * gets a link to «Проверка функций», which explains them and saves a combined report.
     */
    private static void addCheckupLink(Activity a) {
        android.view.View root = a.findViewById(android.R.id.content);
        if (root == null) return;
        root.post(() -> {
            android.view.ViewGroup column = column(root);
            if (column == null || a.isFinishing()) return;
            android.widget.Button link = new android.widget.Button(a);
            link.setText("Почему не работает функция? → Проверка функций");
            link.setAllCaps(false);
            link.setOnClickListener(v -> a.startActivity(new android.content.Intent(a, CheckupActivity.class)));
            column.addView(link, Math.min(2, column.getChildCount()));
        });
    }

    /** android.R.id.content → ScrollView → vertical LinearLayout. */
    private static android.view.ViewGroup column(android.view.View view) {
        for (int depth = 0; depth < 4 && view instanceof android.view.ViewGroup; depth++) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            if (group instanceof android.widget.LinearLayout
                    && ((android.widget.LinearLayout) group).getOrientation() == android.widget.LinearLayout.VERTICAL) {
                return group;
            }
            if (group.getChildCount() == 0) return null;
            view = group.getChildAt(0);
        }
        return null;
    }

    private static boolean isMain(Activity a) {
        return "ru.replyai.android.MainActivity".equals(a.getClass().getName());
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
