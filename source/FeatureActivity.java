package ru.replyai.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Shared lifecycle-safe UI and authenticated requests for the added screens. */
abstract class FeatureActivity extends Activity {
    protected LinearLayout body;
    protected TextView status;
    protected ThemeManager.Palette colors;
    protected final ArrayList<View> controls = new ArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    protected boolean busy;
    private boolean closed;
    private AlertDialog dialog;

    @Override public void onCreate(Bundle state) {
        ThemeManager.apply(this);
        super.onCreate(state);
        colors = ThemeManager.colors(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(colors.background);
        // Also applies on Android 15 where target-35 activities are edge-to-edge.
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(12), dp(20), dp(28));
        scroll.addView(body);
        setContentView(scroll);
        Button back = new Button(this);
        back.setText("‹  На главное меню");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        body.addView(back);
    }

    protected int dp(int value) { return HomeFeatures.dp(this, value); }
    protected TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(colors.ink);
        view.setPadding(0, dp(9), 0, dp(9));
        body.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }
    protected void title(String value) { text(value, 27).setTypeface(null, Typeface.BOLD); }
    protected Button button(String label, View.OnClickListener listener) {
        Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        view.setOnClickListener(listener);
        body.addView(view, new LinearLayout.LayoutParams(-1, -2));
        controls.add(view);
        return view;
    }

    // ------------------------------------------------------------ 2.9 helpers for list screens

    /** A vertical container added to the screen; its content can be rebuilt. */
    protected LinearLayout section() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        body.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    protected LinearLayout card(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(colors.card);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), colors.border);
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        parent.addView(card, lp);
        return card;
    }

    protected TextView label(LinearLayout parent, CharSequence value, int size, boolean muted) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(muted ? colors.muted : colors.ink);
        view.setPadding(0, dp(3), 0, dp(3));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    protected TextView heading(LinearLayout parent, CharSequence value) {
        TextView view = label(parent, value, 17, false);
        view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    protected EditText field(LinearLayout parent, String hint, String value, int inputType) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setText(value);
        view.setInputType(inputType);
        view.setTextColor(colors.ink);
        view.setHintTextColor(colors.muted);
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        controls.add(view);
        return view;
    }

    /** A button inside a rebuilt list; registered so it is disabled while a request runs. */
    protected Button action(LinearLayout parent, String value, View.OnClickListener listener, ArrayList<View> owned) {
        Button view = new Button(this);
        view.setText(value);
        view.setAllCaps(false);
        view.setOnClickListener(listener);
        view.setEnabled(!busy);
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        controls.add(view);
        owned.add(view);
        return view;
    }

    /** Before a list is rebuilt: forget its old buttons and fields. */
    protected void release(ArrayList<View> owned) {
        controls.removeAll(owned);
        owned.clear();
    }

    protected void confirm(String title, String message, String ok, Runnable action, Runnable cancel) {
        if (dialog != null) dialog.dismiss();
        dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Отмена", (d, w) -> { if (cancel != null) cancel.run(); })
                .setPositiveButton(ok, (d, w) -> action.run())
                .create();
        dialog.setOnCancelListener(d -> { if (cancel != null) cancel.run(); });
        dialog.show();
    }

    protected static String time(double epochSeconds) {
        if (epochSeconds <= 0) return "—";
        return new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(new Date((long) (epochSeconds * 1000)));
    }

    protected JSONObject request(String method, String path, JSONObject data) throws Exception {
        RemoteSettings settings = new RemoteSettings(this);
        if (settings.url().isEmpty() || settings.token().isEmpty()) {
            throw new Exception("Сначала сохраните адрес VPS и ключ управления в разделе «Подключение к VPS».");
        }
        return new RemoteClient(settings.url(), settings.token()).call(method, path, data);
    }

    interface Task { JSONObject run() throws Exception; }
    interface Result { void accept(JSONObject value) throws Exception; }

    protected void execute(Task task, Result result) {
        if (busy || closed) return;
        busy = true;
        for (View v : controls) v.setEnabled(false);
        if (status != null) status.setText("Загрузка…");
        worker.execute(() -> {
            JSONObject response = null;
            Exception failure = null;
            try { response = task.run(); } catch (Exception e) { failure = e; }
            final JSONObject value = response;
            final Exception error = failure;
            ui.post(() -> {
                if (closed || isFinishing() || isDestroyed()) return;
                busy = false;
                for (View v : controls) v.setEnabled(true);
                try {
                    if (error != null) throw error;
                    result.accept(value);
                } catch (Exception e) { showError(e); }
            });
        });
    }

    static final String OLD_SERVER = "На VPS работает старая версия сервера: этой функции в ней нет. "
            + "Нажмите «Обновить сервер» — сервер из этого приложения установится поверх, настройки и вход в Telegram сохранятся.";

    /** 404 / Not Found: the screen needs a newer server than the one on the VPS. */
    static boolean oldServer(String message) {
        String m = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        return m.contains("404") || m.contains("not found");
    }

    protected void showError(Exception e) {
        String message = e.getMessage();
        if (oldServer(message)) {
            message = OLD_SERVER;
            showUpdateButton();
        } else if (message == null || message.length() > 400) {
            message = "Не удалось выполнить запрос. Проверьте подключение к VPS.";
        }
        if (status != null) status.setText(message);
    }

    private Button update;

    /** Opens «Подключение к VPS» (installation of the bundled server). */
    protected void showUpdateButton() {
        if (update != null || status == null || !(status.getParent() instanceof LinearLayout)) return;
        LinearLayout parent = (LinearLayout) status.getParent();
        update = new Button(this);
        update.setText("Обновить сервер");
        update.setAllCaps(false);
        update.setOnClickListener(v -> openServerSetup(this));
        parent.addView(update, parent.indexOfChild(status) + 1, new LinearLayout.LayoutParams(-1, -2));
    }

    static void openServerSetup(Activity a) {
        try {
            a.startActivity(new android.content.Intent().setClassName(a, "ru.replyai.android.ServerSetupActivity"));
        } catch (RuntimeException error) {
            android.widget.Toast.makeText(a, "Откройте «1. Подключение к VPS» в главном меню.", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onDestroy() {
        if (dialog != null) dialog.dismiss();
        closed = true;
        ui.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        super.onDestroy();
    }
}
