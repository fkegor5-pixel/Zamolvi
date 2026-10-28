package ru.replyai.android;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;

/* JADX INFO: loaded from: classes.dex */
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
            this.ink = Color.parseColor(z ? "#E8EEF7" : "#182A3E");
            this.muted = Color.parseColor(z ? "#A0AEC0" : "#607386");
            this.accent = Color.parseColor(z ? "#63BFFF" : "#2196D2");
            this.background = Color.parseColor(z ? "#101820" : "#F2F6FB");
            this.card = Color.parseColor(z ? "#1B2633" : "#FFFFFF");
            this.soft = Color.parseColor(z ? "#253A4D" : "#E5F2FB");
            this.border = Color.parseColor(z ? "#344252" : "#E1E9F3");
        }
    }
}
