package ru.replyai.android;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import org.json.JSONObject;

/**
 * Checks GitHub Releases for a newer version of the application.
 *
 * Only a check: the APK is downloaded and installed by the owner from the release page, the way
 * every other build of this application is installed. Nothing is sent to GitHub except the request.
 */
final class Updates {
    static final String REPOSITORY = "fkegor5-pixel/Zamolvi";
    static final String LATEST_API = "https://api.github.com/repos/" + REPOSITORY + "/releases/latest";
    static final String LATEST_PAGE = "https://github.com/" + REPOSITORY + "/releases/latest";
    static final String PREFS = "replyai_features";
    static final String ENABLED = "upd_enabled";     // the daily check, on by default
    static final String CHECKED_AT = "upd_checked_at";
    static final String LATEST = "upd_latest";       // version of the newest release, "" while unknown
    static final String NOTES = "upd_notes";
    static final String PAGE = "upd_page";
    static final String FAILED = "upd_failed";       // why the last check did not finish
    static final String TOLD = "upd_told";           // version the owner was already notified about
    static final String CHANNEL = "zamolvi_update";
    private static final int TIMEOUT_MS = 15_000;
    private static final int NOTES_LIMIT = 1200;

    private Updates() { }

    /** True when the release version is newer than the installed one. Both may carry a leading «v». */
    static boolean newer(String installed, String release) {
        int[] a = parts(installed), b = parts(release);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
            if (x != y) return y > x;
        }
        return false;
    }

    /** «v3.5.2» → 3, 5, 2; anything that is not a number ends the version. */
    private static int[] parts(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("v") || text.startsWith("V")) text = text.substring(1);
        String[] chunks = text.split("\\.");
        int[] numbers = new int[chunks.length];
        for (int i = 0; i < chunks.length; i++) {
            int digits = 0;
            while (digits < chunks[i].length() && Character.isDigit(chunks[i].charAt(digits))) digits++;
            if (digits == 0) return java.util.Arrays.copyOf(numbers, i);
            numbers[i] = Integer.parseInt(chunks[i].substring(0, digits));
        }
        return numbers;
    }

    /** Asks GitHub for the newest release and records the answer. Returns the version, "" when there is none. */
    static String check(Context c) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(LATEST_API).openConnection();
        String version, notes, page;
        try {
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", Brand.NAME + "/" + Brand.VERSION); // GitHub requires it
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            int code = connection.getResponseCode();
            if (code == 404) { // a repository without releases
                remember(c, "", "", LATEST_PAGE, "");
                return "";
            }
            if (code != 200) throw new Exception("GitHub ответил " + code);
            JSONObject release = new JSONObject(read(connection.getInputStream()));
            version = release.optString("tag_name", release.optString("name", "")).trim();
            notes = release.optString("body", "").trim();
            if (notes.length() > NOTES_LIMIT) notes = notes.substring(0, NOTES_LIMIT) + "…";
            page = release.optString("html_url", LATEST_PAGE);
        } finally {
            connection.disconnect();
        }
        remember(c, version, notes, page, "");
        return version;
    }

    private static String read(InputStream in) throws Exception {
        try (InputStream stream = in) {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            for (int n; (n = stream.read(buffer)) > 0; ) data.write(buffer, 0, n);
            return data.toString("UTF-8");
        }
    }

    private static void remember(Context c, String version, String notes, String page, String failure) {
        c.getSharedPreferences(PREFS, 0).edit()
                .putString(LATEST, version).putString(NOTES, notes).putString(PAGE, page)
                .putString(FAILED, failure).putLong(CHECKED_AT, System.currentTimeMillis()).apply();
    }

    static void failed(Context c, Exception error) {
        String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        c.getSharedPreferences(PREFS, 0).edit().putString(FAILED, reason)
                .putLong(CHECKED_AT, System.currentTimeMillis()).apply();
    }

    static String installed() {
        return Brand.VERSION;
    }

    /** Checks and, when the release is newer and was not announced yet, shows one notification. */
    static void checkInBackground(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        if (!p.getBoolean(ENABLED, true)) return;
        try {
            String version = check(c);
            if (version.isEmpty() || !newer(installed(), version)) return;
            if (version.equals(p.getString(TOLD, ""))) return; // already announced
            p.edit().putString(TOLD, version).apply();
            announce(c, version);
        } catch (Exception error) {
            failed(c, error); // no network: the screen shows it, nothing is retried on its own
        }
    }

    private static void announce(Context c, String version) {
        try {
            android.app.NotificationManager nm = c.getSystemService(android.app.NotificationManager.class);
            nm.createNotificationChannel(new android.app.NotificationChannel(CHANNEL,
                    I18n.t(c, "Обновление Zamolvi"), android.app.NotificationManager.IMPORTANCE_LOW));
            android.app.PendingIntent open = android.app.PendingIntent.getActivity(c, 0,
                    new android.content.Intent(c, UpdateActivity.class),
                    android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
            nm.notify(CHANNEL.hashCode(), new android.app.Notification.Builder(c, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(I18n.t(c, "Доступна новая версия") + ": " + version)
                    .setContentText(I18n.t(c, "Нажмите, чтобы открыть раздел «Обновление»."))
                    .setContentIntent(open).setAutoCancel(true).build());
        } catch (RuntimeException error) {
            // No permission to notify: the version is recorded and shown on the screen anyway.
        }
    }

    static String when(long at) {
        return at <= 0 ? "—" : new java.text.SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
                .format(new java.util.Date(at));
    }
}
