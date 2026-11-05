package ru.replyai.android;

import android.animation.LayoutTransition;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

/** Colors, rounded shapes and motion shared by the screens. The system animation scale is respected by the animators. */
final class Ui {
    /** Saturated tones that keep white text readable on both light and dark themes. */
    private static final int[] ACCENTS = {0xFF4F46E5, 0xFF7C3AED, 0xFF0891B2, 0xFF059669, 0xFFEA580C, 0xFFDB2777, 0xFF2563EB};

    private static final String PREFS = "replyai_features", COLORFUL = "colorful";
    /** The default look: the teal of the icon as the only accent, flat buttons, quiet cards. Colorful is opt-in. */
    static boolean colorful = false;
    private static int brand = 0xFF0B7A75;

    private Ui() { }

    static void load(android.content.Context c) { colorful = c.getSharedPreferences(PREFS, 0).getBoolean(COLORFUL, false); }

    /** For every new screen: the brand teal is lighter on the dark theme. */
    static void theme(android.content.Context c) { brand = ThemeManager.colors(c).accent; }

    static void save(android.content.Context c, boolean value) {
        colorful = value;
        c.getSharedPreferences(PREFS, 0).edit().putBoolean(COLORFUL, value).apply();
    }

    static int accent(int index) { return colorful ? ACCENTS[Math.floorMod(index, ACCENTS.length)] : brand; }

    /** Text on a filled color: dark on the light teal of the dark theme, white otherwise. */
    static int onColor(int color) { return Color.luminance(color) > 0.45f ? 0xFF06302C : 0xFFFFFFFF; }

    static int alpha(int color, int alpha) { return (alpha << 24) | (color & 0xFFFFFF); }

    /** The color moved toward another by a share (0..1). */
    static int mix(int from, int to, float share) {
        return Color.rgb(
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * share),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * share),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * share));
    }

    private static int neighbour(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + 24f) % 360f;
        hsv[2] = Math.min(1f, hsv[2] + 0.08f);
        return Color.HSVToColor(hsv);
    }

    static int dp(View v, int n) { return Math.round(n * v.getResources().getDisplayMetrics().density); }

    /** A filled button (a two-tone gradient in the colorful look), ripple and a soft press. */
    static void filled(Button button, int color) {
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{color, colorful ? neighbour(color) : color});
        fill.setCornerRadius(dp(button, colorful ? 14 : 12));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), fill, null));
        button.setTextColor(colorful ? 0xFFFFFFFF : onColor(color));
        button.setAllCaps(false);
        button.setMinHeight(dp(button, 48));
        button.setStateListAnimator(null); // the flat gradient has no default elevation shadow
        press(button);
    }

    /** A secondary button: accent text on a light tint of the accent, flat. */
    static void tonal(Button button, int color, int soft) {
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(soft);
        fill.setCornerRadius(dp(button, 12));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(color, 0x33)), fill, null));
        button.setTextColor(color);
        button.setAllCaps(false);
        button.setMinHeight(dp(button, 48));
        button.setStateListAnimator(null);
    }

    /** The quiet «back» button: a tinted pill in the colorful look, plain accent text otherwise. */
    static void pill(Button button, int color) {
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(colorful ? alpha(color, 0x26) : 0);
        fill.setCornerRadius(dp(button, colorful ? 20 : 12));
        GradientDrawable mask = new GradientDrawable(); // a transparent fill would hide the ripple
        mask.setColor(0xFFFFFFFF);
        mask.setCornerRadius(dp(button, colorful ? 20 : 12));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(color, 0x44)), fill, mask));
        button.setTextColor(color);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(dp(button, 40));
        button.setPadding(dp(button, colorful ? 14 : 4), dp(button, 8), dp(button, 14), dp(button, 8));
        button.setStateListAnimator(null);
        press(button);
    }

    /** A rounded field: an accent outline in the colorful look, a hairline gray one otherwise. */
    static void input(EditText field, int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0x14808080);
        shape.setCornerRadius(dp(field, 12));
        shape.setStroke(dp(field, 1), colorful ? alpha(color, 0xAA) : 0x40808080);
        field.setBackground(shape);
        field.setPadding(dp(field, 14), dp(field, 12), dp(field, 14), dp(field, 12));
    }

    static void tint(Switch toggle, int color) {
        int[][] states = {{android.R.attr.state_checked}, {}};
        toggle.setThumbTintList(new ColorStateList(states, new int[]{color, 0xFFB4BAC8}));
        toggle.setTrackTintList(new ColorStateList(states, new int[]{alpha(color, 0x88), 0x55888888}));
    }

    /** Heading text painted with a gradient across its width. */
    static void gradientText(TextView view, int from, int to) {
        if (!colorful) {
            view.getPaint().setShader(null);
            return;
        }
        float width = Math.max(1f, view.getPaint().measureText(view.getText().toString()));
        view.getPaint().setShader(new LinearGradient(0, 0, width, 0, from, to, Shader.TileMode.CLAMP));
        view.invalidate();
    }

    /** The view shrinks a little under the finger and springs back; the touch itself is not consumed. */
    static void press(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    float down = colorful ? 0.96f : 0.98f;
                    v.animate().scaleX(down).scaleY(down).setDuration(90).setInterpolator(null).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(colorful ? 220 : 160)
                            .setInterpolator(colorful ? new OvershootInterpolator(2.2f) : new DecelerateInterpolator()).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** The screen content fades in while rising a little. */
    static void reveal(View view) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(dp(view, 14));
        view.animate().alpha(1f).translationY(0f).setDuration(280).setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    /** The first rows of a column appear one after another. */
    static void stagger(ViewGroup column) {
        int count = Math.min(column.getChildCount(), 14);
        for (int i = 0; i < column.getChildCount(); i++) {
            View child = column.getChildAt(i);
            if (i >= count) continue;
            child.setAlpha(0f);
            child.setTranslationY(dp(child, 18));
            child.animate().alpha(1f).translationY(0f).setStartDelay(i * 40L).setDuration(300)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
    }

    /** Rows added to the column later fade in and the others glide to their new place. */
    static void smooth(ViewGroup column) {
        LayoutTransition transition = new LayoutTransition();
        transition.setDuration(240);
        transition.disableTransitionType(LayoutTransition.DISAPPEARING);
        transition.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
        column.setLayoutTransition(transition);
    }
}
