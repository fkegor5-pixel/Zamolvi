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
            {"replyai.update.navigation.v1", "16. Обновление", "Версия приложения и выпуски на GitHub"},
            {"replyai.notification.navigation.v1", "17. Ответы из уведомлений", "Без сервера, с телефона: WhatsApp, VK, Viber, SMS"},
            {"replyai.style.navigation.v1", "18. Стиль общения", "Тон ответов: деловой, добрый, дерзкий…"},
            {"replyai.tips.navigation.v1", "19. 💡 Подсказки", "Где взять сервер, бесплатные нейросети и советы"},
            {"replyai.commands.navigation.v1", "20. ⚡ Свои команды", "Слово, где искать и как ответить — отвечает нейросеть"},
    };
    private static final Class<?>[] SCREENS = {DeletedMessagesActivity.class, MemoryActivity.class,
            MediaActivity.class, ApprovalActivity.class, StatsActivity.class, CheckupActivity.class,
            UpdateActivity.class, NotificationReplyActivity.class, StyleActivity.class, TipsActivity.class, CommandsActivity.class};
    private static final String LANGUAGE_TAG = "replyai.language.v1";
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
        int lastRawCount = -1;   // children before our additions: tells a new screen from a refresh of the same one
        long revealedAt;
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
            if (isHome(content)) {
                colorMenu(activity, content);
                group(activity, content);
                stripNumbers(content);
            }
            long now = SystemClock.elapsedRealtime();
            if (count != lastRawCount && now - revealedAt > 800) { // a different screen: the content glides in
                Ui.reveal(content);
                revealedAt = now;
            }
            lastRawCount = count;
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
                if (startsWith(value, AUTOREPLY_PREFIX) || startsWith(value, I18n.t(a, AUTOREPLY_PREFIX))) hookAutoReply(a, text);
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
        logo.setImageResource(a.getApplicationInfo().icon); // the app's own icon, not the messenger logo
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
    /** Buttons added inside cards of original sections: card title, tag, button text; screens in the same order. */
    private static final String[][] SECTION_ENTRIES = {
            {"Выберите тему", LANGUAGE_TAG, "Язык · Language"},
    };
    private static final Class<?>[] SECTION_SCREENS = {LanguageActivity.class};

    private static void addSectionEntries(Activity a, ViewGroup content) {
        for (int i = 0; i < SECTION_ENTRIES.length; i++) {
            String[] e = SECTION_ENTRIES[i];
            if (content.findViewWithTag(e[1]) != null) continue;
            TextView title = findText(content, e[0], a);
            if (title == null || !(title.getParent() instanceof LinearLayout)) continue;
            LinearLayout card = (LinearLayout) title.getParent();
            Button sample = null;
            int at = card.getChildCount();
            for (int j = 0; j < card.getChildCount(); j++) {
                if (card.getChildAt(j) instanceof Button) { sample = (Button) card.getChildAt(j); at = j + 1; }
            }
            Class<?> screen = SECTION_SCREENS[i];
            // Same look as the card's own buttons, right after them.
            Button button = new Button(a);
            button.setText(I18n.t(a, e[2]));
            button.setAllCaps(false);
            if (sample != null) {
                button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, sample.getTextSize());
                button.setTextColor(sample.getTextColors());
                button.setMinHeight(sample.getMinHeight());
                if (sample.getBackground() != null && sample.getBackground().getConstantState() != null) {
                    button.setBackground(sample.getBackground().getConstantState().newDrawable().mutate());
                }
            }
            button.setTag(e[1]);
            button.setOnClickListener(v -> a.startActivity(new Intent(a, screen)));
            LinearLayout.LayoutParams lp = sample != null && sample.getLayoutParams() instanceof LinearLayout.LayoutParams
                    ? new LinearLayout.LayoutParams((LinearLayout.LayoutParams) sample.getLayoutParams())
                    : new LinearLayout.LayoutParams(-1, -2);
            card.addView(button, at, lp);
        }
    }

    /** The view showing this original (Russian) text, before or after translation. */
    private static TextView findText(View v, String value, Activity a) {
        if (v instanceof TextView) {
            CharSequence text = ((TextView) v).getText();
            return value.contentEquals(text) || I18n.t(a, value).contentEquals(text) ? (TextView) v : null;
        }
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), value, a);
                if (found != null) return found;
            }
        }
        return null;
    }


    /** Same look as the original menu entries: bold title, muted subtitle, chevron, ripple. */
    private static Button entry(Activity a, String title, String subtitle) {
        ThemeManager.Palette colors = ThemeManager.colors(a);
        title = I18n.t(a, title);
        subtitle = I18n.t(a, subtitle);
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

    private static final String GROUP_TAG = "replyai.menu.group.v1";
    private static final String HEAD_TAG = "replyai.menu.head.v1";
    private static final String OPEN_PREF = "menu_open";
    private static final int FOLD_FROM = 12; // fold only a complete menu, never one still being built
    /**
     * Folded main menu: group title, then the numbers of the entries inside it, in the order shown.
     * Connect first, then how to answer, what is remembered, the numbers, and the app itself.
     */
    private static final String[][] GROUPS = {
            {"Подключения", "1.", "2.", "17."},
            {"Ответы", "5.", "4.", "3.", "18.", "20.", "13."},
            {"Данные и память", "11.", "12.", "10."},
            {"Статистика", "8.", "14.", "6."},
            {"Приложение", "19.", "9.", "15.", "16.", "7."},
    };

    /** The 15 entries are folded into four groups; one is open at a time and the choice is remembered. */
    private static void group(Activity a, ViewGroup menu) {
        if (menu.findViewWithTag(HEAD_TAG + "0") != null) return; // already folded
        java.util.List<Button> rows = new java.util.ArrayList<>();
        for (int i = 0; i < menu.getChildCount(); i++) {
            View child = menu.getChildAt(i);
            if (child instanceof Button && number(((Button) child).getText()) != null) rows.add((Button) child);
        }
        if (rows.size() < FOLD_FROM) return;
        int at = menu.indexOfChild(rows.get(0));
        for (Button row : rows) menu.removeView(row);
        int open = a.getSharedPreferences("replyai_features", 0).getInt(OPEN_PREF, -1);
        java.util.List<Button> placed = new java.util.ArrayList<>();
        int cursor = at;
        for (int g = 0; g < GROUPS.length; g++) {
            LinearLayout box = new LinearLayout(a);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setTag(GROUP_TAG + g);
            for (int k = 1; k < GROUPS[g].length; k++) {
                for (Button row : rows) {
                    if (!GROUPS[g][k].equals(number(row.getText())) || placed.contains(row)) continue;
                    placed.add(row);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                    lp.topMargin = dp(a, 8);
                    box.addView(row, lp);
                }
            }
            if (box.getChildCount() == 0) continue;
            box.setVisibility(g == open ? View.VISIBLE : View.GONE);
            final int index = g;
            Button head = head(a, g);
            label(a, head, g, box.getChildCount(), g == open);
            head.setOnClickListener(v -> toggle(a, menu, index));
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
            hp.topMargin = dp(a, 11);
            menu.addView(head, cursor++, hp);
            menu.addView(box, cursor++, new LinearLayout.LayoutParams(-1, -2));
        }
        // An entry of a newer version that no group knows about stays visible below the groups.
        for (Button row : rows) {
            if (placed.contains(row)) continue;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(a, 11);
            menu.addView(row, cursor++, lp);
        }
        Ui.smooth(menu);
    }

    private static void toggle(Activity a, ViewGroup menu, int index) {
        android.content.SharedPreferences p = a.getSharedPreferences("replyai_features", 0);
        int now = p.getInt(OPEN_PREF, -1) == index ? -1 : index;
        p.edit().putInt(OPEN_PREF, now).apply();
        for (int g = 0; g < GROUPS.length; g++) {
            View box = menu.findViewWithTag(GROUP_TAG + g);
            View head = menu.findViewWithTag(HEAD_TAG + g);
            if (box == null || !(head instanceof Button)) continue;
            box.setVisibility(g == now ? View.VISIBLE : View.GONE);
            label(a, (Button) head, g, ((ViewGroup) box).getChildCount(), g == now);
        }
    }

    /** «Ответы · 4   ▾» — already translated, so the branding pass leaves it alone. */
    private static void label(Activity a, Button head, int group, int count, boolean open) {
        head.setText(I18n.t(a, GROUPS[group][0]) + " · " + count + (open ? "   ▾" : "   ▸"));
    }

    private static Button head(Activity a, int group) {
        ThemeManager.Palette colors = ThemeManager.colors(a);
        int accent = Ui.accent(group);
        Button head = new Button(a);
        head.setTag(HEAD_TAG + group);
        head.setAllCaps(false);
        head.setTextSize(17);
        head.setTextColor(colors.ink);
        head.setTypeface(null, android.graphics.Typeface.BOLD);
        head.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        head.setMinHeight(dp(a, 56));
        head.setPadding(dp(a, 18), dp(a, 10), dp(a, 14), dp(a, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.mix(colors.card, accent, 0.18f));
        bg.setCornerRadius(dp(a, 18));
        bg.setStroke(dp(a, 2), Ui.alpha(accent, 0xC0));
        head.setBackground(new RippleDrawable(ColorStateList.valueOf(Ui.alpha(accent, 0x44)), bg, null));
        head.setStateListAnimator(null);
        Ui.press(head);
        return head;
    }

    /**
     * «1. Подключение к VPS» → «Подключение к VPS» for every entry of the menu, inside the groups too.
     * Runs on each pass: the original screen rewrites the text of an entry when its status changes,
     * and the number comes back with it. The numbers still identify the entries while they are folded.
     */
    private static void stripNumbers(ViewGroup menu) {
        for (int i = 0; i < menu.getChildCount(); i++) {
            View child = menu.getChildAt(i);
            if (child instanceof Button) {
                strip((Button) child);
            } else if (child instanceof ViewGroup && tagged(child, GROUP_TAG)) {
                ViewGroup box = (ViewGroup) child;
                for (int k = 0; k < box.getChildCount(); k++) {
                    if (box.getChildAt(k) instanceof Button) strip((Button) box.getChildAt(k));
                }
            }
        }
    }

    /** Drops the leading number of one entry; subSequence keeps the styles of the title and subtitle. */
    private static void strip(Button row) {
        if (tagged(row, HEAD_TAG)) return; // a group header, not an entry
        CharSequence text = row.getText();
        String digits = number(text);
        if (digits == null) return;
        int cut = digits.length();
        while (cut < text.length() && text.charAt(cut) == ' ') cut++;
        if (cut >= text.length() || text.charAt(cut) == '\n') return;
        row.setText(text.subSequence(cut, text.length()));
    }

    private static boolean tagged(View view, String prefix) {
        return view.getTag() instanceof String && ((String) view.getTag()).startsWith(prefix);
    }

    /** Leading «12.» of a menu entry, null for any other button; the number stays in every language. */
    private static String number(CharSequence value) {
        if (value == null) return null;
        int i = 0;
        while (i < value.length() && Character.isDigit(value.charAt(i))) i++;
        return i > 0 && i < value.length() && value.charAt(i) == '.' ? value.subSequence(0, i + 1).toString() : null;
    }

    /** Menu rows get their own color: a tinted card, an accent outline and a dot, instead of one gray list. */
    private static void colorMenu(Activity a, ViewGroup menu) {
        ThemeManager.Palette colors = ThemeManager.colors(a);
        int tone = 0;
        for (int i = 0; i < menu.getChildCount(); i++) {
            if (!(menu.getChildAt(i) instanceof Button)) continue;
            Button row = (Button) menu.getChildAt(i);
            if (tagged(row, HEAD_TAG)) continue;
            int accent = Ui.accent(tone++);
            GradientDrawable card = new GradientDrawable();
            card.setColor(Ui.mix(colors.card, accent, 0.10f));
            card.setCornerRadius(dp(a, 18));
            card.setStroke(dp(a, 2), Ui.alpha(accent, 0xB0));
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(Ui.alpha(accent, 0x44)), card, null));
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(accent);
            dot.setSize(dp(a, 12), dp(a, 12));
            android.graphics.drawable.Drawable[] now = row.getCompoundDrawables();
            row.setCompoundDrawablesWithIntrinsicBounds(dot, now[1], now[2], now[3]);
            row.setCompoundDrawablePadding(dp(a, 12));
            Ui.press(row);
        }
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
