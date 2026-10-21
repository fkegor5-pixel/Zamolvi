package ru.replyai.android;

import android.content.Intent;
import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;

/** «Язык · Language»: interface language; the app restarts so that every screen is rebuilt. */
public final class LanguageActivity extends FeatureActivity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        title("Язык · Language");
        text("Язык меню, экранов и сообщений сервера. Нейросеть всё равно отвечает собеседнику на его языке.", 15);
        String current = getSharedPreferences("replyai_features", 0).getString(I18n.PREF, "");
        RadioGroup group = new RadioGroup(this);
        for (int i = 0; i < I18n.LANGUAGES.length; i++) {
            RadioButton radio = new RadioButton(this);
            radio.setId(i + 1);
            radio.setText(I18n.LANGUAGES[i][1]);
            radio.setTextColor(colors.ink);
            radio.setPadding(0, dp(10), 0, dp(10));
            group.addView(radio);
            if (I18n.LANGUAGES[i][0].equals(current)) radio.setChecked(true);
        }
        body.addView(group);
        group.setOnCheckedChangeListener((g, id) -> {
            I18n.choose(this, I18n.LANGUAGES[id - 1][0]);
            Intent start = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (start != null) {
                startActivity(start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            }
            finish();
        });
    }
}
