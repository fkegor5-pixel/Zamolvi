package ru.replyai.android;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.TextView;
import java.util.WeakHashMap;

/**
 * Product name and version shown to the user.
 *
 * The screens of the original application (main menu, server setup, QR login, diagnostics)
 * live in its unchanged DEX and still carry the former name in their code. They are rewritten
 * at runtime: every visible text is passed through {@link #text(CharSequence)} whenever the
 * screen content changes. Internal identifiers (package, preferences, server paths) keep their
 * old values so that existing installations and servers continue to work.
 */
public final class Brand {
    public static final String NAME = "Zamolvi";
    public static final String VERSION = "3.0";
    public static final String AUTHOR = "@stytam";
    public static final String VERSION_LABEL = NAME + " " + VERSION + " · " + AUTHOR;
    static final String FORMER = "Reply AI";

    private static final String[][] REPLACEMENTS = {
            // Device-specific wording of the original QR instructions.
            {"на Tecno откройте", "на этом телефоне откройте"},
            {"вход в Telegram на Tecno.", "вход в Telegram на этом телефоне."},
            // «7. Как это работает»: the description of the original app predates the added features.
            {"AI-автоответчик для личных чатов Telegram. Получает новое сообщение и отвечает с помощью выбранной нейросети.",
                    "AI-автоответчик для личных чатов Telegram. Получает новое сообщение и отвечает с помощью "
                            + "выбранной нейросети. Расшифровывает голосовые, читает фото и документы, помнит главное "
                            + "о собеседниках, сохраняет удалённые сообщения и может отправлять ответы только после "
                            + "вашего согласования."},
            {FORMER, NAME},
    };
    private static final java.util.regex.Pattern VERSION_LINE =
            java.util.regex.Pattern.compile("(Reply AI|" + NAME + ") \\d[\\d.]* · ");
    private static final WeakHashMap<Activity, Watcher> watchers = new WeakHashMap<>();

    private Brand() { }

    /** True for the title of the home screen, before or after it was rewritten. */
    static boolean isTitle(CharSequence value) {
        return value != null && (FORMER.contentEquals(value) || NAME.contentEquals(value));
    }

    /** The original version line «Reply AI 2.7 · …» becomes the current one. */
    static boolean isVersionLine(CharSequence value) {
        if (value == null) return false;
        return VERSION_LINE.matcher(value).lookingAt();
    }

    static CharSequence text(CharSequence value) {
        if (value == null || value.length() == 0) return value;
        if (isVersionLine(value)) return VERSION_LABEL.contentEquals(value) ? value : VERSION_LABEL;
        String text = value.toString();
        String result = text;
        for (String[] pair : REPLACEMENTS) {
            // A replacement that extends the original text must not be applied to its own result again.
            if (pair[1].contains(pair[0]) && result.contains(pair[1])) continue;
            result = result.replace(pair[0], pair[1]);
        }
        return result.equals(text) ? value : result;
    }

    /** Rewrites texts and hints of the view tree. User input in edit fields is never touched. */
    static void rewrite(View view) {
        if (view instanceof TextView) {
            TextView field = (TextView) view;
            if (!(view instanceof EditText)) {
                CharSequence value = field.getText();
                CharSequence fixed = text(value);
                if (fixed != value) field.setText(fixed);
            }
            CharSequence hint = field.getHint();
            CharSequence fixedHint = text(hint);
            if (fixedHint != hint) field.setHint(fixedHint);
        }
        CharSequence description = view.getContentDescription();
        CharSequence fixedDescription = text(description);
        if (fixedDescription != description) view.setContentDescription(fixedDescription);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) rewrite(group.getChildAt(i));
        }
    }

    /** Keeps an original screen rewritten while it rebuilds its content. */
    static void attach(Activity activity) {
        View root = activity.getWindow().getDecorView();
        CharSequence title = activity.getTitle();
        CharSequence fixed = text(title);
        if (fixed != title) activity.setTitle(fixed);
        Watcher watcher = new Watcher(root);
        watchers.put(activity, watcher);
        root.getViewTreeObserver().addOnGlobalLayoutListener(watcher);
        root.post(watcher);
    }

    static void detach(Activity activity) {
        Watcher watcher = watchers.remove(activity);
        if (watcher == null) return;
        watcher.root.removeCallbacks(watcher);
        ViewTreeObserver observer = watcher.root.getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnGlobalLayoutListener(watcher);
    }

    private static final class Watcher implements ViewTreeObserver.OnGlobalLayoutListener, Runnable {
        final View root;
        boolean scheduled;

        Watcher(View root) {
            this.root = root;
        }

        @Override public void onGlobalLayout() {
            // Many layout passes per frame (status polling) collapse into one walk.
            if (!scheduled) {
                scheduled = true;
                root.post(this);
            }
        }

        @Override public void run() {
            scheduled = false;
            rewrite(root);
        }
    }
}
