package ru.replyai.android;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.yandex.mobile.ads.banner.BannerAdSize;
import com.yandex.mobile.ads.banner.BannerAdView;
import com.yandex.mobile.ads.common.AdRequest;
import com.yandex.mobile.ads.common.MobileAds;

/** A sticky banner of the Yandex Advertising Network at the bottom of the menu and of every section. */
final class AdBanner {
    // shortcut: Yandex demo block until the real «Баннер» block ID is set here.
    static final String AD_UNIT = "demo-banner-yandex";
    private static final String TAG = "zamolvi.banner";
    private static final String COLUMN = "zamolvi.banner.column";
    private static boolean initialized;

    private AdBanner() { }

    /** Screens without a banner: the intro video and QR login (the code must stay fully visible). */
    static boolean wanted(Activity a) {
        String name = a.getClass().getName();
        return name.startsWith("ru.replyai.android.") && !name.endsWith(".VideoAdActivity")
                && !name.endsWith(".QrLoginActivity");
    }

    /** Moves the screen into a column with the banner under it; once per screen. */
    static void attach(Activity a) {
        ViewGroup content = a.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() != 1 || content.findViewWithTag(TAG) != null) return;
        if (!initialized) {
            MobileAds.initialize(a.getApplicationContext(), () -> { });
            initialized = true;
        }
        View screen = content.getChildAt(0);
        content.removeView(screen);
        LinearLayout column = new LinearLayout(a);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setTag(COLUMN);
        column.addView(screen, new LinearLayout.LayoutParams(-1, 0, 1));
        FrameLayout holder = new FrameLayout(a);
        holder.setTag(TAG);
        holder.setBackgroundColor(ThemeManager.colors(a).background);
        column.addView(holder, new LinearLayout.LayoutParams(-1, -2));
        content.addView(column, new FrameLayout.LayoutParams(-1, -1));

        int keyboard = HomeFeatures.dp(a, 200);
        // The screen keeps its own top and side insets; the bottom one goes under the banner.
        column.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom = insets.getSystemWindowInsetBottom();
            boolean typing = bottom > keyboard;  // no banner above the keyboard
            holder.setVisibility(typing ? View.GONE : View.VISIBLE);
            holder.setPadding(0, 0, 0, typing ? 0 : bottom);
            screen.dispatchApplyWindowInsets(insets.replaceSystemWindowInsets(insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), typing ? bottom : 0));
            return insets.consumeSystemWindowInsets();
        });
        column.requestApplyInsets();

        BannerAdView banner = new BannerAdView(a);
        banner.setAdUnitId(AD_UNIT);
        int width = Math.round(a.getResources().getDisplayMetrics().widthPixels / a.getResources().getDisplayMetrics().density);
        banner.setAdSize(BannerAdSize.stickySize(a, width));
        holder.addView(banner, new FrameLayout.LayoutParams(-1, -2));
        banner.loadAd(new AdRequest.Builder().build());
    }

    /** The screen itself when it was moved into the banner column; the view otherwise. */
    static View unwrap(View view) {
        return view != null && COLUMN.equals(view.getTag()) ? ((ViewGroup) view).getChildAt(0) : view;
    }

    static void detach(Activity a) {
        View holder = a.findViewById(android.R.id.content);
        holder = holder == null ? null : holder.findViewWithTag(TAG);
        if (holder instanceof ViewGroup && ((ViewGroup) holder).getChildAt(0) instanceof BannerAdView) {
            ((BannerAdView) ((ViewGroup) holder).getChildAt(0)).destroy();
        }
    }
}
