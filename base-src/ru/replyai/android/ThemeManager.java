package ru.replyai.android;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
public final class ThemeManager {
    private ThemeManager() {
    }

    public static String choice(Context context) {
        String string = context.getSharedPreferences("reply_ai_appearance", 0).getString("theme", "system");
        return ("dark".equals(string) || "light".equals(string)) ? string : "system";
    }

    public static void save(Context context, String str) {
        if (!"dark".equals(str) && !"light".equals(str) && !"system".equals(str)) {
            throw new IllegalArgumentException();
        }
        context.getSharedPreferences("reply_ai_appearance", 0).edit().putString("theme", str).apply();
    }

    public static boolean dark(Context context) {
        if (choice(context).equals("dark")) {
            return true;
        }
        return choice(context).equals("system") && (context.getResources().getConfiguration().uiMode & 48) == 32;
    }

    public static void apply(Activity activity) {
        activity.setTheme(dark(activity) ? R.style.AppThemeDark : R.style.AppTheme);
    }

    public static Palette colors(Context context) {
        return new Palette(dark(context));
    }

    public static final class Palette {
        public final int accent;
        public final int background;
        public final int border;
        public final int card;
        public final boolean dark;
        public final int ink;
        public final int muted;
        public final int soft;

        private Palette(boolean z) {
            this.dark = z;
            // Neutral gray surfaces and one accent: the teal of the app icon (lighter on dark for contrast).
            this.ink = Color.parseColor(z ? "#E8EBEE" : "#15191D");
            this.muted = Color.parseColor(z ? "#9BA3AC" : "#5D6670");
            this.accent = Color.parseColor(z ? "#5CCFC3" : "#0B7A75");
            this.background = Color.parseColor(z ? "#0E1113" : "#F4F5F6");
            this.card = Color.parseColor(z ? "#171B1F" : "#FFFFFF");
            this.soft = Color.parseColor(z ? "#1B2A29" : "#E5F1F0");
            this.border = Color.parseColor(z ? "#262C32" : "#E1E4E8");
        }
    }
}
