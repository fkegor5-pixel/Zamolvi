package ru.replyai.android;

import java.util.Locale;
import java.util.regex.Pattern;

/** Decisions of the notification replies that need no Android classes: apps, profiles, rules. */
final class Rules {
    /** Messengers whose notifications can be answered: key, title, packages. Only Telegram is on by default. */
    static final String[][] APPS = {
            {"telegram", "Telegram", "org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.messenger.beta",
                    "org.telegram.plus", "org.thunderdog.challegram", "tw.nekomimi.nekogram", "nekox.messenger"},
            {"whatsapp", "WhatsApp", "com.whatsapp", "com.whatsapp.w4b"},
            {"vk", "VK", "com.vkontakte.android"},
            {"viber", "Viber", "com.viber.voip"},
            {"sms", "SMS", "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
                    "com.android.messaging"},
    };
    /** Profiles of the owner's situation: title and the default note for the AI. */
    static final String[][] PROFILES = {
            {"Работаю", "Я сейчас на работе и занят. Ответь коротко, что отвечу позже; если срочно — пусть звонит."},
            {"Сплю", "Я сплю. Скажи, что отвечу утром; если что-то срочное — пусть позвонит."},
            {"За рулём", "Я за рулём и не могу писать. Отвечу, как только остановлюсь; в экстренном случае пусть звонит."},
    };
    private static final Pattern DIGITS = Pattern.compile("(?<!\\d)\\d{4,8}(?!\\d)");
    private static final String[] CODE_WORDS = {"код", "code", "пароль", "password", "пин", "pin", "cvv", "cvc", "otp", "подтвержд"};

    private Rules() { }

    /** Key of the messenger for a package name, null for any other application. */
    static String app(String pkg) {
        for (String[] row : APPS) {
            for (int i = 2; i < row.length; i++) if (row[i].equals(pkg)) return row[0];
        }
        return null;
    }

    /** Minutes after midnight for «H:MM» or «HH:MM»; -1 for an empty or invalid time. */
    static int minutes(String value) {
        if (value == null) return -1;
        String[] part = value.trim().split(":");
        if (part.length != 2) return -1;
        try {
            int h = Integer.parseInt(part[0].trim()), m = Integer.parseInt(part[1].trim());
            return h < 0 || h > 23 || m < 0 || m > 59 ? -1 : h * 60 + m;
        } catch (NumberFormatException error) {
            return -1;
        }
    }

    /** True when the minute of the day is inside from..to; the range may pass midnight (22:00–07:00). */
    static boolean inRange(int now, int from, int to) {
        if (from < 0 || to < 0 || from == to) return false;
        return from < to ? now >= from && now < to : now >= from || now < to;
    }

    /** Index of the active profile: one switched on by hand first, then the first whose time has come; -1 if none. */
    static int activeProfile(boolean[] manual, int[] from, int[] to, int now) {
        for (int i = 0; i < manual.length; i++) if (manual[i]) return i;
        for (int i = 0; i < manual.length; i++) if (inRange(now, from[i], to[i])) return i;
        return -1;
    }

    /** Lines «слово=ответ»: the reply of the first line whose word is in the message; null when none matches. */
    static String fixedReply(String rules, String text) {
        String low = text.toLowerCase(Locale.ROOT);
        for (String line : rules.split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String word = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String reply = line.substring(eq + 1).trim();
            if (!word.isEmpty() && !reply.isEmpty() && low.contains(word)) return reply;
        }
        return null;
    }

    /** True when the message contains a word of the list (one per line, compared without case). */
    static boolean hasAny(String words, String text) {
        String low = text.toLowerCase(Locale.ROOT);
        for (String line : words.split("\n")) {
            String word = line.trim().toLowerCase(Locale.ROOT);
            if (!word.isEmpty() && low.contains(word)) return true;
        }
        return false;
    }

    /** A login or payment code: such a message must never be sent to the AI server. */
    static boolean looksLikeCode(String text) {
        if (!DIGITS.matcher(text).find()) return false;
        String low = text.toLowerCase(Locale.ROOT);
        for (String word : CODE_WORDS) if (low.contains(word)) return true;
        return false;
    }
}
