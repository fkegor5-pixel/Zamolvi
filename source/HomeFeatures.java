package ru.replyai.android;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * Adds 2.8 features to the unchanged MainActivity.
 *
 * MainActivity rebuilds its whole screen in showScreen(), so the view tree is walked only
 * when the screen content actually changes, not on every layout pass (status polling).
 */
public final class HomeFeatures {
    private static final String TAG = "ZamolviFeatures";
    /** Added menu entries: tag, title, subtitle; screens in the same order. */
    private static final String[][] ENTRIES = {
            {"replyai.archive.navigation.v1", "10. Удалённые сообщения", "Архив и настройки сохранения"},
            {"replyai.memory.navigation.v1", "11. Память собеседников", "Имя, интересы, договорённости, факты"},
            {"replyai.media.navigation.v1", "12. Фото и документы", "Текст с фото, изображения, PDF и DOCX"},
            {"replyai.approval.navigation.v1", "13. Согласование ответов", "Черновики: отправить, исправить, отклонить"},
            {"replyai.stats.navigation.v1", "14. Подробная статистика", "Расходы AI, чаты, время ответа, ошибки"},
            {"replyai.checkup.navigation.v1", "15. Проверка функций", "Что работает, что нет и почему"},
    };
    private static final Class<?>[] SCREENS = {DeletedMessagesActivity.class, MemoryActivity.class,
            MediaActivity.class, ApprovalActivity.class, StatsActivity.class, CheckupActivity.class};
    private static final String NOTIFY_TAG = "replyai.notification.reply.v1";
    private static final String STYLE_TAG = "replyai.style.v1";
    private static final String LOGO_TAG = "replyai.developer.logo.v1";
    private static final String AUTOREPLY_TAG = "replyai.autoreply.intro.v1";
    private static final String AUTOREPLY_PREFIX = "5. Автоответчик";
    private static final long INTRO_DEBOUNCE_MS = 1200;

    private static final WeakHashMap<Activity, Hook> hooks = new WeakHashMap<>();

    // Resolved once: MainActivity.showScreen(Screen.CONTROL). Same APK, so no hidden-API limits.
    private static Method showScreen;
    private static Object controlScreen;
    private static boolean resolved;
    private static long lastIntroAt;

    // Main-menu scroll position, kept across section visits (static: survives screen rebuilds).
    private static int homeScrollY;

    private HomeFeatures() { }

    private static final class Hook implements ViewTreeObserver.OnGlobalLayoutListener {
        final Activity activity;
        final View root;
        boolean scheduled;
        Object lastFirstChild;
        int lastChildCount = -1;
        ScrollView scroll;       // MainActivity's root ScrollView
        boolean wasHome;         // the home menu was showing on the previous scan
        boolean restoring;       // a restore is in flight; ignore scroll changes meanwhile
        View homeFirst;          // first row of the menu currently on screen, null on sections
        final Runnable scan = new Runnable() {
            @Override public void run() {
                scheduled = false;
                scanIfChanged();
            }
        };

        Hook(Activity activity, View root) {
            this.activity = activity;
            this.root = root;
        }

        @Override public void onGlobalLayout() {
            // Several layout passes per frame collapse into one check.
            if (!scheduled) {
                scheduled = true;
                root.post(scan);
            }
        }

        void scanIfChanged() {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            ViewGroup content = screenContent(root);
            if (content == null) {
                install(activity, root);
                return;
            }
            trackScroll(content);
            Object first = content.getChildCount() > 0 ? content.getChildAt(0) : null;
            int count = content.getChildCount();
            if (first == lastFirstChild && count == lastChildCount) return;
            install(activity, content);
            addSectionEntries(activity, content);
            // Our own additions change the count; remember the state after installing.
            lastFirstChild = content.getChildCount() > 0 ? content.getChildAt(0) : null;
            lastChildCount = content.getChildCount();
            restoreHomeScroll(content);
        }

        /** Record the scroll position while the home menu is on screen. */
        private void trackScroll(ViewGroup content) {
            View parent = content.getParent() instanceof ScrollView ? (ScrollView) content.getParent() : null;
            if (!(parent instanceof ScrollView)) return;
            if (scroll != parent) {
                scroll = (ScrollView) parent;
                scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> {
                    // When a section opens, the menu rows are removed first and the app then resets the
                    // scroll to 0; that reset must not overwrite the saved position. Record only while
                    // the very same menu is still on screen.
                    if (restoring || homeFirst == null) return;
                    ViewGroup now = screenContent(root);
                    if (now != null && now.getChildCount() > 0 && now.getChildAt(0) == homeFirst) homeScrollY = y;
                });
            }
        }

        /** On the transition back to the home menu, restore its previous scroll position. */
        private void restoreHomeScroll(ViewGroup content) {
            boolean home = isHome(content);
            if (home && !wasHome && scroll != null && homeScrollY > 0) {
                restoring = true;
                final int target = homeScrollY;
                // After this layout pass the menu has its full height, so the scroll is not clamped away.
                scroll.post(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed()) scroll.scrollTo(0, target);
                    scroll.post(() -> restoring = false);
                });
            }
            wasHome = home;
            homeFirst = home && content.getChildCount() > 0 ? content.getChildAt(0) : null;
        }
    }

    /** The home menu is the only screen whose first child row carries the product title. */
    private static boolean isHome(ViewGroup content) {
        if (content.getChildCount() == 0 || !(content.getChildAt(0) instanceof LinearLayout)) return false;
        LinearLayout header = (LinearLayout) content.getChildAt(0);
        return header.getChildCount() > 1 && header.getChildAt(0) instanceof ImageView && containsTitle(header);
    }

    static void attach(Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        Hook hook = new Hook(activity, root);
        hooks.put(activity, hook);
        root.getViewTreeObserver().addOnGlobalLayoutListener(hook);
        root.post(hook.scan);
    }

    static void detach(Activity activity) {
        Hook hook = hooks.remove(activity);
        if (hook == null) return;
        hook.root.removeCallbacks(hook.scan);
        ViewTreeObserver observer = hook.root.getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnGlobalLayoutListener(hook);
    }

    /** android.R.id.content → ScrollView → LinearLayout (MainActivity.content). */
    private static ViewGroup screenContent(View root) {
        if (!(root instanceof ViewGroup) || ((ViewGroup) root).getChildCount() == 0) return null;
        View scroll = ((ViewGroup) root).getChildAt(0);
        if (!(scroll instanceof ScrollView) || ((ScrollView) scroll).getChildCount() == 0) return null;
        View content = ((ScrollView) scroll).getChildAt(0);
        return content instanceof ViewGroup ? (ViewGroup) content : null;
    }

    private static void install(Activity a, View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            CharSequence value = text.getText();
            if (view instanceof Button) {
                if (startsWith(value, AUTOREPLY_PREFIX)) hookAutoReply(a, text);
            } else if (Brand.isVersionLine(value) && !Brand.VERSION_LABEL.contentEquals(value)) {
                text.setText(Brand.VERSION_LABEL);
            }
            return;
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        // Home has a logo row as its first child; other sections start with Back.
        if (group instanceof LinearLayout && group.getChildCount() > 0 && group.getChildAt(0) instanceof LinearLayout) {
            LinearLayout header = (LinearLayout) group.getChildAt(0);
            if (header.getChildCount() > 1 && header.getChildAt(0) instanceof ImageView && containsTitle(header)) {
                hookLogo(a, (ImageView) header.getChildAt(0));
                addEntries(a, group);
            }
        }
        for (int i = 0; i < group.getChildCount(); i++) install(a, group.getChildAt(i));
    }

    private static boolean startsWith(CharSequence value, String prefix) {
        return value != null && value.length() >= prefix.length()
                && value.subSequence(0, prefix.length()).toString().equals(prefix);
    }

    /** «5. Автоответчик»: opens the control screen and plays the video on top of it. */
    private static void hookAutoReply(Activity a, TextView button) {
        if (AUTOREPLY_TAG.equals(button.getTag())) return;
        if (!resolveNavigation(a)) return; // keep the original behaviour untouched
        button.setTag(AUTOREPLY_TAG);
        button.setOnClickListener(v -> {
            if (a.isFinishing() || a.isDestroyed()) return;
            long now = SystemClock.elapsedRealtime();
            if (now - lastIntroAt < INTRO_DEBOUNCE_MS) return; // double tap
            lastIntroAt = now;
            try {
                showScreen.invoke(a, controlScreen);
            } catch (Exception error) {
                Log.w(TAG, "Cannot open control screen", error);
                return;
            }
            VideoAdActivity.play(a);
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static synchronized boolean resolveNavigation(Activity a) {
        if (resolved) return showScreen != null;
        resolved = true;
        try {
            Class<?> screen = Class.forName("ru.replyai.android.MainActivity$Screen", false, a.getClassLoader());
            Method method = a.getClass().getDeclaredMethod("showScreen", screen);
            method.setAccessible(true);
            controlScreen = Enum.valueOf((Class) screen, "CONTROL");
            showScreen = method;
        } catch (Throwable error) {
            Log.w(TAG, "Auto-reply video hook unavailable", error);
            showScreen = null;
        }
        return showScreen != null;
    }

    private static void hookLogo(Activity a, ImageView logo) {
        if (LOGO_TAG.equals(logo.getTag())) return;
        logo.setTag(LOGO_TAG);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        logo.setContentDescription("Логотип " + Brand.NAME + ". Три нажатия — режим разработчика");
        logo.setOnClickListener(new View.OnClickListener() {
            int count;
            long last;
            @Override public void onClick(View v) {
                long now = SystemClock.elapsedRealtime();
                count = now - last <= 1800 ? count + 1 : 1;
                last = now;
                if (count == 3) {
                    count = 0;
                    a.getSharedPreferences("replyai_features", 0).edit().putBoolean("developer", true).apply();
                    Toast.makeText(a, "Режим разработчика включён", Toast.LENGTH_SHORT).show();
                    a.startActivity(new Intent(a, DeveloperActivity.class));
                }
            }
        });
    }

    private static void addEntries(Activity a, ViewGroup group) {
        for (int i = 0; i < ENTRIES.length; i++) {
            if (group.findViewWithTag(ENTRIES[i][0]) != null) continue;
            Class<?> screen = SCREENS[i];
            Button button = entry(a, ENTRIES[i][1], ENTRIES[i][2]);
            button.setTag(ENTRIES[i][0]);
            button.setOnClickListener(v -> a.startActivity(new Intent(a, screen)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(a, 11);
            // Before the footer, so the entries keep their order.
            group.addView(button, Math.max(0, group.getChildCount() - 1), lp);
        }
    }

    /** Entries added to original sections: card title that marks the section, tag, title, subtitle. */
    private static final String[][] SECTION_ENTRIES = {
            {"Личный аккаунт", NOTIFY_TAG, "Ответы из уведомлений", "Автоответ без входа в Telegram на сервере"},
            {"AI API и ответы", STYLE_TAG, "Стиль общения", "Добрый, дерзкий, гопник, умник и другие"},
    };
    private static final Class<?>[] SECTION_SCREENS = {NotificationReplyActivity.class, StyleActivity.class};

    private static void addSectionEntries(Activity a, ViewGroup content) {
        for (int i = 0; i < SECTION_ENTRIES.length; i++) {
            String[] e = SECTION_ENTRIES[i];
            if (content.findViewWithTag(e[1]) != null || !hasText(content, e[0])) continue;
            Class<?> screen = SECTION_SCREENS[i];
            Button button = entry(a, e[2], e[3]);
            button.setTag(e[1]);
            button.setOnClickListener(v -> a.startActivity(new Intent(a, screen)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(a, 11);
            content.addView(button, lp);
        }
    }

    private static boolean hasText(View v, String value) {
        if (v instanceof TextView && value.contentEquals(((TextView) v).getText())) return true;
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) if (hasText(group.getChildAt(i), value)) return true;
        }
        return false;
    }

    /** Same look as the original menu entries: bold title, muted subtitle, chevron, ripple. */
    private static Button entry(Activity a, String title, String subtitle) {
        ThemeManager.Palette colors = ThemeManager.colors(a);
        SpannableString text = new SpannableString(title + "\n" + subtitle);
        text.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, title.length(), 33);
        text.setSpan(new RelativeSizeSpan(0.78f), title.length() + 1, text.length(), 33);
        text.setSpan(new ForegroundColorSpan(colors.muted), title.length() + 1, text.length(), 33);
        Button button = new Button(a);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(colors.ink);
        button.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        button.setMinHeight(dp(a, 78));
        button.setPadding(dp(a, 18), dp(a, 12), dp(a, 14), dp(a, 12));
        int chevron = a.getResources().getIdentifier("ic_chevron", "drawable", a.getPackageName());
        if (chevron != 0) {
            button.setCompoundDrawablesWithIntrinsicBounds(0, 0, chevron, 0);
            button.setCompoundDrawablePadding(dp(a, 8));
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(colors.card);
        bg.setCornerRadius(dp(a, 18));
        bg.setStroke(dp(a, 1), colors.border);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(colors.border), bg, null));
        return button;
    }

    private static boolean containsTitle(View v) {
        if (v instanceof TextView && Brand.isTitle(((TextView) v).getText())) return true;
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) if (containsTitle(group.getChildAt(i))) return true;
        }
        return false;
    }

    static int dp(Activity a, int n) { return Math.round(n * a.getResources().getDisplayMetrics().density); }
}
