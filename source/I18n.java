package ru.replyai.android;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;

/**
 * Interface language. The base application and the server speak Russian; every visible text is
 * translated on the phone from assets/i18n/&lt;lang&gt;.json: the whole text when it is known,
 * otherwise each known fragment (whole words only, longest first) — this also covers texts that
 * are assembled at run time, such as «Ответов: 5».
 */
public final class I18n {
    static final String PREF = "ui_language";
    /** code, native name; "" follows the phone. */
    static final String[][] LANGUAGES = {
            {"", "Как на телефоне · System"}, {"ru", "Русский"}, {"en", "English"}, {"es", "Español"},
            {"pt", "Português"}, {"de", "Deutsch"}, {"tr", "Türkçe"}, {"uk", "Українська"}};
    private static final int MIN_FRAGMENT = 2; // whole words only, so «Да» never hits «Дата»

    private static String loaded;
    private static Map<String, String> exact = new HashMap<>();
    private static Map<Character, List<String>> byFirst = new HashMap<>();

    private I18n() { }

    static String language(Context c) {
        String chosen = c.getSharedPreferences("replyai_features", 0).getString(PREF, "");
        if (!chosen.isEmpty()) return chosen;
        String phone = Locale.getDefault().getLanguage();
        for (String[] l : LANGUAGES) if (!l[0].isEmpty() && l[0].equals(phone)) return phone;
        return "be".equals(phone) || "kk".equals(phone) ? "ru" : "en"; // unknown languages: English
    }

    static void choose(Context c, String code) {
        c.getSharedPreferences("replyai_features", 0).edit().putString(PREF, code).apply();
        synchronized (I18n.class) { loaded = null; }
    }

    private static synchronized void ensure(Context c) {
        String lang = language(c);
        if (lang.equals(loaded)) return;
        Map<String, String> table = new HashMap<>();
        if (!"ru".equals(lang)) {
            try (InputStream in = c.getAssets().open("i18n/" + lang + ".json")) {
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                byte[] buffer = new byte[16384];
                for (int n; (n = in.read(buffer)) > 0; ) data.write(buffer, 0, n);
                JSONObject json = new JSONObject(data.toString("UTF-8"));
                for (Iterator<String> i = json.keys(); i.hasNext(); ) {
                    String key = i.next();
                    table.put(key, json.getString(key));
                }
            } catch (Exception missing) {
                table.clear(); // no file: stay in Russian rather than show half a language
            }
        }
        Map<Character, List<String>> index = new HashMap<>();
        for (String key : table.keySet()) {
            if (key.length() < MIN_FRAGMENT) continue;
            index.computeIfAbsent(Character.toLowerCase(key.charAt(0)), x -> new ArrayList<>()).add(key);
        }
        for (List<String> list : index.values()) list.sort((a, b) -> b.length() - a.length());
        exact = table;
        byFirst = index;
        loaded = lang;
    }

    /** The text in the chosen language; the same object when nothing changes. */
    static CharSequence t(Context c, CharSequence value) {
        if (value == null || value.length() == 0 || !cyrillic(value)) return value;
        ensure(c);
        if (exact.isEmpty()) return value;
        String text = value.toString();
        String whole = exact.get(text.trim());
        if (whole != null) return text.replace(text.trim(), whole);
        StringBuilder out = new StringBuilder(text.length() + 16);
        int i = 0;
        boolean changed = false;
        while (i < text.length()) {
            char ch = text.charAt(i);
            String hit = null;
            if (!letter(i > 0 ? text.charAt(i - 1) : ' ')) {
                List<String> candidates = byFirst.get(Character.toLowerCase(ch));
                if (candidates != null) {
                    for (String key : candidates) {
                        int end = i + key.length();
                        if (end <= text.length() && text.regionMatches(i, key, 0, key.length())
                                && (end == text.length() || !letter(text.charAt(end)))) {
                            hit = key;
                            break;
                        }
                    }
                }
            }
            if (hit != null) {
                out.append(exact.get(hit));
                i += hit.length();
                changed = true;
            } else {
                out.append(ch);
                i++;
            }
        }
        return changed ? out.toString() : value;
    }

    static String t(Context c, String value) {
        return value == null ? null : t(c, (CharSequence) value).toString();
    }

    /**
     * Where an offset of the original text lands after every line was translated on its own.
     * Used to put the styles of a menu entry («title\nsubtitle») back on the translated lines.
     */
    static int remap(int offset, String[] lines, String[] out) {
        int from = 0, to = 0;
        for (int i = 0; i < lines.length; i++) {
            int end = from + lines[i].length();
            if (offset <= end) {
                int within = offset - from;
                return to + (within >= lines[i].length() ? out[i].length() : Math.min(within, out[i].length()));
            }
            from = end + 1;
            to += out[i].length() + 1;
        }
        return Math.max(0, to - 1); // past the end of the text
    }

    private static boolean letter(char ch) {
        return Character.isLetterOrDigit(ch);
    }

    private static boolean cyrillic(CharSequence s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= 'Ѐ' && ch <= 'ӿ') return true;
        }
        return false;
    }
}
