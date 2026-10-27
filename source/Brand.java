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
    public static final String VERSION = "3.7";
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

    /** Texts this pass already produced: unchanged views are skipped instead of translated again. */
    private static final WeakHashMap<View, String> done = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> pressable = new WeakHashMap<>();

    /** Name, device wording and interface language for one text; styles of menu entries are kept. */
    static CharSequence localize(android.content.Context c, CharSequence value) {
        CharSequence named = text(value);
        if (named instanceof android.text.Spanned) return styled(c, (android.text.Spanned) named);
        return I18n.t(c, named);
    }

    /**
     * A styled text, translated line by line with its spans moved to the matching new lines.
     * The menu entries of the original screens are built this way: a bold title and a smaller,
     * muted subtitle, so translating the whole text at once would drop the styles.
     */
    private static CharSequence styled(android.content.Context c, android.text.Spanned source) {
        String[] lines = source.toString().split("\n", -1);
        String[] out = new String[lines.length];
        boolean changed = false;
        for (int i = 0; i < lines.length; i++) {
            out[i] = I18n.t(c, lines[i]);
            if (!out[i].equals(lines[i])) changed = true;
        }
        if (!changed) return source;
        android.text.SpannableString result = new android.text.SpannableString(String.join("\n", out));
        for (Object span : source.getSpans(0, source.length(), Object.class)) {
            int start = I18n.remap(source.getSpanStart(span), lines, out);
            int end = I18n.remap(source.getSpanEnd(span), lines, out);
            if (start < end) result.setSpan(span, start, end, source.getSpanFlags(span));
        }
        return result;
    }

    /** Rewrites texts and hints of the view tree. User input in edit fields is never touched. */
    static void rewrite(View view) {
        android.content.Context c = view.getContext();
        if (view instanceof android.widget.Button && !pressable.containsKey(view)) {
            pressable.put(view, Boolean.TRUE);
            Ui.press(view); // every button of every screen springs under the finger
        }
        if (view instanceof TextView) {
            TextView field = (TextView) view;
            if (!(view instanceof EditText)) {
                CharSequence value = field.getText();
                if (value != null && !value.toString().equals(done.get(view))) {
                    CharSequence fixed = localize(c, value);
                    if (fixed != value) field.setText(fixed);
                    done.put(view, field.getText().toString());
                }
            }
            CharSequence hint = field.getHint();
            CharSequence fixedHint = localize(c, hint);
            if (fixedHint != hint) field.setHint(fixedHint);
        }
        CharSequence description = view.getContentDescription();
        CharSequence fixedDescription = localize(c, description);
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
        CharSequence fixed = localize(activity, title);
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
