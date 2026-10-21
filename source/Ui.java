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

    private Ui() { }

    static int accent(int index) { return ACCENTS[Math.floorMod(index, ACCENTS.length)]; }

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

    /** A button filled with a two-tone gradient, white text, ripple and a soft press. */
    static void filled(Button button, int color) {
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{color, neighbour(color)});
        fill.setCornerRadius(dp(button, 14));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), fill, null));
        button.setTextColor(0xFFFFFFFF);
        button.setAllCaps(false);
        button.setMinHeight(dp(button, 48));
        button.setStateListAnimator(null); // the flat gradient has no default elevation shadow
        press(button);
    }

    /** A tinted text pill: the quiet «back» button. */
    static void pill(Button button, int color) {
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(alpha(color, 0x26));
        fill.setCornerRadius(dp(button, 20));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(color, 0x44)), fill, null));
        button.setTextColor(color);
        button.setAllCaps(false);
        button.setStateListAnimator(null);
        press(button);
    }

    /** A rounded field with an accent outline. */
    static void input(EditText field, int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0x14808080);
        shape.setCornerRadius(dp(field, 12));
        shape.setStroke(dp(field, 1), alpha(color, 0xAA));
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
        float width = Math.max(1f, view.getPaint().measureText(view.getText().toString()));
        view.getPaint().setShader(new LinearGradient(0, 0, width, 0, from, to, Shader.TileMode.CLAMP));
        view.invalidate();
    }

    /** The view shrinks a little under the finger and springs back; the touch itself is not consumed. */
    static void press(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).setInterpolator(null).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new OvershootInterpolator(2.2f)).start();
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
