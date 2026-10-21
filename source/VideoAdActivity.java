package ru.replyai.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

/**
 * Plays the bundled video when «5. Автоответчик» is pressed.
 * Opens on top of the already shown control screen and simply closes afterwards.
 */
public final class VideoAdActivity extends Activity implements
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {
    private static final String TAG = "ZamolviIntro";
    private static final String POSITION = "intro_position_ms";
    private static final long PREPARE_TIMEOUT_MS = 8000;
    private static final long SKIP_DELAY_MS = 1500;
    private static int videoRes = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = this::close;
    private VideoView videoView;
    private TextView skip;
    private final Runnable showSkip = () -> { if (skip != null) skip.setVisibility(View.VISIBLE); };
    private boolean closed;
    private boolean resumed;
    private boolean prepared;
    private int playbackPosition;

    /** Starts the video if the APK contains it; otherwise does nothing. */
    static void play(Activity from) {
        if (resolveVideo(from) == 0) return;
        try {
            from.startActivity(new Intent(from, VideoAdActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));
            from.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (RuntimeException error) {
            Log.w(TAG, "Cannot start video", error);
        }
    }

    private static int resolveVideo(Activity a) {
        if (videoRes < 0) videoRes = a.getResources().getIdentifier("replyai_intro", "raw", a.getPackageName());
        return videoRes;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemBars();
        playbackPosition = state == null ? 0 : Math.max(0, state.getInt(POSITION, 0));
        int video = resolveVideo(this);
        if (video == 0) {
            close();
            return;
        }
        try {
            FrameLayout root = new FrameLayout(this);
            root.setBackgroundColor(Color.BLACK);
            videoView = new VideoView(this);
            root.addView(videoView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
            root.addView(skip = skipButton(), skipParams());
            setContentView(root);
            videoView.setOnCompletionListener(this);
            videoView.setOnErrorListener(this);
            videoView.setOnPreparedListener(player -> {
                if (closed) return;
                prepared = true;
                player.setLooping(false);
                if (resumed) startPlayback();
            });
            // A packaged raw resource needs no file copy, storage permission or cache.
            videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + video));
        } catch (RuntimeException error) {
            Log.w(TAG, "Cannot prepare video", error);
            close();
        }
    }

    private TextView skipButton() {
        float d = getResources().getDisplayMetrics().density;
        TextView view = new TextView(this);
        view.setText("Пропустить  ›");
        view.setTextColor(Color.WHITE);
        view.setTextSize(15);
        view.setPadding(Math.round(16 * d), Math.round(9 * d), Math.round(16 * d), Math.round(9 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x99000000);
        bg.setCornerRadius(20 * d);
        bg.setStroke(Math.round(d), 0x66FFFFFF);
        view.setBackground(bg);
        view.setVisibility(View.GONE);
        view.setContentDescription("Пропустить видео");
        view.setOnClickListener(v -> close());
        return view;
    }

    private FrameLayout.LayoutParams skipParams() {
        float d = getResources().getDisplayMetrics().density;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        int margin = Math.round(24 * d);
        lp.setMargins(margin, margin, margin, margin);
        return lp;
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void startPlayback() {
        if (!resumed || closed || videoView == null) return;
        try {
            handler.removeCallbacks(timeout);
            int duration = videoView.getDuration();
            if (duration > 0 && playbackPosition >= duration) {
                close();
                return;
            }
            if (playbackPosition > 0) videoView.seekTo(playbackPosition);
            videoView.start();
            handler.postDelayed(showSkip, SKIP_DELAY_MS);
            // Recover if a decoder starts but never sends completion.
            long remaining = duration > 0 ? duration - playbackPosition : 15000;
            handler.postDelayed(timeout, Math.min(30000, Math.max(1000, remaining)) + 3000);
        } catch (RuntimeException error) {
            Log.w(TAG, "Cannot play video", error);
            close();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        hideSystemBars();
        if (!closed) {
            if (prepared) startPlayback();
            else handler.postDelayed(timeout, PREPARE_TIMEOUT_MS);
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(timeout);
        if (!closed && videoView != null) {
            try {
                playbackPosition = videoView.getCurrentPosition();
                videoView.pause();
            } catch (RuntimeException ignored) { }
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        int position = playbackPosition;
        if (videoView != null && prepared) {
            try { position = videoView.getCurrentPosition(); }
            catch (RuntimeException ignored) { }
        }
        state.putInt(POSITION, Math.max(0, position));
        super.onSaveInstanceState(state);
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) hideSystemBars();
    }

    @Override
    public void onCompletion(MediaPlayer player) { close(); }

    @Override
    public boolean onError(MediaPlayer player, int what, int extra) {
        Log.w(TAG, "Playback error " + what + "/" + extra);
        close();
        return true;
    }

    @Override
    public void onBackPressed() { close(); }

    /** The control screen is already underneath, so closing is enough. */
    private void close() {
        if (closed) return;
        closed = true;
        handler.removeCallbacksAndMessages(null);
        if (!isFinishing()) {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

    @Override
    protected void onDestroy() {
        closed = true;
        handler.removeCallbacksAndMessages(null);
        if (videoView != null) {
            videoView.setOnPreparedListener(null);
            videoView.setOnCompletionListener(null);
            videoView.setOnErrorListener(null);
            try { videoView.stopPlayback(); }
            catch (RuntimeException ignored) { }
            videoView = null;
        }
        skip = null;
        super.onDestroy();
    }
}
