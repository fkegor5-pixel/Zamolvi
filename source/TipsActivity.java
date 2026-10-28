package ru.replyai.android;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.util.Linkify;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yandex.mobile.ads.common.AdError;
import com.yandex.mobile.ads.common.AdRequestConfiguration;
import com.yandex.mobile.ads.common.AdRequestError;
import com.yandex.mobile.ads.common.ImpressionData;
import com.yandex.mobile.ads.common.MobileAds;
import com.yandex.mobile.ads.rewarded.Reward;
import com.yandex.mobile.ads.rewarded.RewardedAd;
import com.yandex.mobile.ads.rewarded.RewardedAdEventListener;
import com.yandex.mobile.ads.rewarded.RewardedAdLoadListener;
import com.yandex.mobile.ads.rewarded.RewardedAdLoader;

/**
 * «Подсказки»: practical tips that open for a day after a rewarded ad of the Yandex Advertising Network.
 * When no ad can be loaded the tips open anyway: the user is never left without them.
 */
public final class TipsActivity extends FeatureActivity {
    static final String AD_UNIT = "R-M-20292618-1";
    private static final String PREFS = "zamolvi_tips";
    private static final String OPEN_UNTIL = "open_until";
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    /** Title, then the text. Prices and limits are as of October 2026 and change: they say «примерно». */
    static final String[][] TIPS = {
            {"🖥 Где арендовать сервер",
                    "Берите сервер в Европе (Нидерланды, Германия, Финляндия), а не в России: с российских адресов "
                    + "не работают Groq и Gemini. Хватит тарифа с 1 ГБ памяти, система Debian 12 или Ubuntu 24.04.\n\n"
                    + "• Timeweb Cloud — примерно от 190 ₽/мес, есть Нидерланды, Германия, Казахстан: timeweb.cloud\n"
                    + "• RuVDS — примерно от 150 ₽/мес, есть Германия, Нидерланды, Казахстан: ruvds.com\n"
                    + "• Aeza — примерно от 260 ₽/мес, Европа: aeza.net\n"
                    + "• VDSina — примерно от 330 ₽/мес: vdsina.com\n"
                    + "• ProfitServer — много стран: profitserver.ru\n\n"
                    + "Цены «от» по рейтингу hostinghub.ru, проверьте на сайте перед оплатой."},
            {"🤖 Бесплатные нейросети",
                    "• Groq — около 1000 запросов в день без карты; там же бесплатно расшифровываются голосовые: console.groq.com\n"
                    + "• Google AI Studio (Gemini) — бесплатно без карты, не работает с российских адресов: aistudio.google.com\n"
                    + "• Cerebras — около 1 млн токенов в день без карты: cloud.cerebras.ai\n"
                    + "• OpenRouter — 20+ бесплатных моделей, около 50 запросов в день: openrouter.ai\n"
                    + "• Mistral — бесплатный тариф Experiment: console.mistral.ai\n"
                    + "• GigaChat — работает из России, есть бесплатный лимит для физлиц: developers.sber.ru\n\n"
                    + "Лимиты бесплатных тарифов меняются, смотрите их на сайте сервиса."},
            {"💸 Как тратить меньше лимитов ИИ",
                    "Включите «Объединение сообщений» в разделе «Автоответчик»: пять сообщений подряд от одного "
                    + "человека уйдут одним запросом, а не пятью. Ограничьте автоответы нужными чатами в «Личных чатах»."},
            {"🔒 Защита сервера",
                    "Вход по SSH-ключу вместо пароля, fail2ban от подбора паролей и автоматические обновления Debian "
                    + "(unattended-upgrades). Открытыми держите только порты 22, 80 и 443."},
            {"🛡 Как не получить ограничения в Telegram",
                    "Отвечайте только выбранным чатам, не включайте автоответ всем подряд и оставьте паузу перед "
                    + "ответом. Слишком быстрые и одинаковые ответы многим людям похожи на спам."},
            {"🔔 Ответы из уведомлений не приходят?",
                    "Разрешите Zamolvi работать в фоне: Настройки → Приложения → Zamolvi → Батарея → «Без ограничений». "
                    + "На Xiaomi включите «Автозапуск», на TECNO/Infinix и Honor — запуск в фоне, и закрепите Zamolvi "
                    + "замком в списке недавних приложений."},
            {"✍️ Чтобы ответы были похожи на ваши",
                    "В «Нейросеть и текст» напишите инструкцию от первого лица, например: «Пиши коротко, на «ты», "
                    + "без смайликов, как будто я сам отвечаю с телефона. Если не знаешь ответа — скажи, что отвечу позже»."},
    };

    private LinearLayout list;
    private Button watch;
    private RewardedAdLoader loader;
    private RewardedAd ad;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        title("💡 Подсказки");
        text("Подсказки открываются на сутки после короткой рекламы. Реклама показывается через Рекламную "
                + "сеть Яндекса и помогает развивать Zamolvi.", 15);
        watch = button("▶  Посмотреть рекламу и открыть", v -> load());
        status = text("", 14);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list, new LinearLayout.LayoutParams(-1, -2));
        MobileAds.initialize(this, () -> { });
        show();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, 0);
    }

    private boolean open() {
        return System.currentTimeMillis() < prefs().getLong(OPEN_UNTIL, 0);
    }

    private void unlock(String note) {
        prefs().edit().putLong(OPEN_UNTIL, System.currentTimeMillis() + DAY_MS).apply();
        status.setText(note);
        show();
    }

    /** Titles only while closed; the whole tips for a day after the reward. */
    private void show() {
        list.removeAllViews();
        boolean shown = open();
        watch.setVisibility(shown ? android.view.View.GONE : android.view.View.VISIBLE);
        for (String[] tip : TIPS) {
            LinearLayout box = card(list);
            heading(box, (shown ? "" : "🔒 ") + tip[0]);
            if (shown) {
                TextView text = label(box, tip[1], 15, false);
                Linkify.addLinks(text, Linkify.WEB_URLS);
            }
        }
    }

    private void load() {
        watch.setEnabled(false);
        status.setText("Загружаю рекламу…");
        if (loader == null) {
            loader = new RewardedAdLoader(this);
            loader.setAdLoadListener(new RewardedAdLoadListener() {
                @Override public void onAdLoaded(RewardedAd loaded) {
                    if (isFinishing() || isDestroyed()) return;
                    ad = loaded;
                    play();
                }

                @Override public void onAdFailedToLoad(AdRequestError error) {
                    if (isFinishing() || isDestroyed()) return;
                    watch.setEnabled(true);
                    unlock("Реклама сейчас не загрузилась — подсказки открыты бесплатно на сутки.");
                }
            });
        }
        loader.loadAd(new AdRequestConfiguration.Builder(AD_UNIT).build());
    }

    private void play() {
        ad.setAdEventListener(new RewardedAdEventListener() {
            @Override public void onAdShown() { }
            @Override public void onAdFailedToShow(AdError error) {
                watch.setEnabled(true);
                unlock("Рекламу не удалось показать — подсказки открыты бесплатно на сутки.");
            }
            @Override public void onAdDismissed() {
                watch.setEnabled(true);
                release();
                if (!open()) status.setText("Досмотрите рекламу до конца, чтобы открыть подсказки.");
            }
            @Override public void onAdClicked() { }
            @Override public void onAdImpression(ImpressionData data) { }
            @Override public void onRewarded(Reward reward) {
                unlock("Спасибо! Подсказки открыты на сутки.");
            }
        });
        ad.show(this);
    }

    private void release() {
        if (ad != null) {
            ad.setAdEventListener(null);
            ad = null;
        }
    }

    @Override protected void onDestroy() {
        if (loader != null) loader.setAdLoadListener(null);
        release();
        super.onDestroy();
    }
}
