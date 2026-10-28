package ru.replyai.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;
public final class MainActivity extends Activity {
    private int INK;
    private int MUTED;
    private int TEAL;
    private ThemeManager.Palette colors;
    private long connectionGeneration;
    private boolean connectionUnavailable;
    private LinearLayout content;
    private TextView detailText;
    private boolean foreground;
    private TextView journalState;
    private JSONObject lastStatus;
    private long lastStatusAt;
    private TextView logsText;
    private boolean polling;
    private ScrollView rootScroll;
    private long screenGeneration;
    private RemoteSettings settings;
    private StatsChartView statsChart;
    private TextView statsNote;
    private TextView statsText;
    private TextView statusText;
    private EditText tokenField;
    private LinearLayout transcriptsList;
    private EditText urlField;
    private TextView voiceState;
    private Screen screen = Screen.HOME;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newFixedThreadPool(3);
    private String observedSettingsUrl = "";
    private final Runnable refresh = new Runnable() { // from class: ru.replyai.android.MainActivity.1
        @Override // java.lang.Runnable
        public void run() {
            if (MainActivity.this.foreground) {
                if (!MainActivity.this.polling && !MainActivity.this.settings.url().isEmpty() && !MainActivity.this.settings.token().isEmpty()) {
                    MainActivity.this.fetchStatus(false);
                }
                MainActivity.this.handler.postDelayed(this, 5000L);
            }
        }
    };
    interface Result {
        void accept(JSONObject jSONObject) throws Exception;
    }
    enum Screen {
        HOME,
        CONNECTION,
        TELEGRAM,
        AI,
        CHATS,
        CONTROL,
        JOURNAL,
        ABOUT,
        STATS,
        APPEARANCE,
        VOICE
    }

    @Override // android.app.Activity
    public void onCreate(Bundle bundle) {
        ThemeManager.apply(this);
        super.onCreate(bundle);
        ThemeManager.Palette paletteColors = ThemeManager.colors(this);
        this.colors = paletteColors;
        this.INK = paletteColors.ink;
        this.MUTED = this.colors.muted;
        this.TEAL = this.colors.accent;
        RemoteSettings remoteSettings = new RemoteSettings(this);
        this.settings = remoteSettings;
        this.observedSettingsUrl = remoteSettings.url();
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ScrollView scrollView = new ScrollView(this);
        this.rootScroll = scrollView;
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(this.colors.background);
        ViewCompat.setOnApplyWindowInsetsListener(scrollView, new OnApplyWindowInsetsListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda15
            @Override // androidx.core.view.OnApplyWindowInsetsListener
            public final WindowInsetsCompat onApplyWindowInsets(View view, WindowInsetsCompat windowInsetsCompat) {
                return MainActivity.lambda$onCreate$0(view, windowInsetsCompat);
            }
        });
        LinearLayout linearLayoutColumn = column();
        this.content = linearLayoutColumn;
        linearLayoutColumn.setPadding(dp(20), dp(22), dp(20), dp(28));
        scrollView.addView(this.content);
        setContentView(scrollView);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, new OnBackInvokedCallback() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda16
                @Override // android.window.OnBackInvokedCallback
                public final void onBackInvoked() {
                    MainActivity.this.backToMenu();
                }
            });
        }
        Screen screenValueOf = Screen.HOME;
        if (bundle != null) {
            try {
                screenValueOf = Screen.valueOf(bundle.getString("section", "HOME"));
            } catch (IllegalArgumentException unused) {
            }
        }
        showScreen(screenValueOf);
    }

    static /* synthetic */ WindowInsetsCompat lambda$onCreate$0(View view, WindowInsetsCompat windowInsetsCompat) {
        Insets insets = windowInsetsCompat.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
        view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
        return windowInsetsCompat;
    }

    private void showScreen(Screen screen) {
        this.screen = screen;
        this.screenGeneration++;
        this.content.removeAllViews();
        this.urlField = null;
        this.tokenField = null;
        this.statusText = null;
        this.detailText = null;
        this.logsText = null;
        this.journalState = null;
        this.statsText = null;
        this.statsNote = null;
        this.statsChart = null;
        this.voiceState = null;
        this.transcriptsList = null;
        if (this.screen != Screen.HOME) {
            Button button = button("‹  На главное меню", false);
            button.setGravity(8388627);
            button.setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda63
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m1718lambda$showScreen$1$rureplyaiandroidMainActivity(view);
                }
            });
            add(this.content, button, 0);
            TextView textViewLabel = label(screenTitle(this.screen), 27, this.INK);
            textViewLabel.setTypeface(null, 1);
            add(this.content, textViewLabel, 20);
        }
        switch (this.screen) {
            case HOME:
                buildHome();
                break;
            case CONNECTION:
                buildConnection();
                break;
            case TELEGRAM:
                buildTelegram();
                break;
            case AI:
                buildAi();
                break;
            case CHATS:
                buildChats();
                break;
            case CONTROL:
                buildControls();
                break;
            case JOURNAL:
                buildJournal();
                break;
            case ABOUT:
                buildAbout();
                break;
            case STATS:
                buildStats();
                break;
            case APPEARANCE:
                buildAppearance();
                break;
            case VOICE:
                buildVoice();
                break;
        }
        TextView textViewLabel2 = label("Reply AI 2.7 · @stytam", 12, this.MUTED);
        textViewLabel2.setGravity(17);
        add(this.content, textViewLabel2, 22);
        renderStatus();
        this.rootScroll.post(new Runnable() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda64
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m1719lambda$showScreen$2$rureplyaiandroidMainActivity();
            }
        });
    }
    /* synthetic */ void m1718lambda$showScreen$1$rureplyaiandroidMainActivity(View view) {
        showScreen(Screen.HOME);
    }
    /* synthetic */ void m1719lambda$showScreen$2$rureplyaiandroidMainActivity() {
        this.rootScroll.scrollTo(0, 0);
    }

    private String screenTitle(Screen screen) {
        switch (screen) {
            case CONNECTION:
                return "Подключение к VPS";
            case TELEGRAM:
                return "Telegram";
            case AI:
                return "Нейросеть и текст";
            case CHATS:
                return "Личные чаты";
            case CONTROL:
                return "Автоответчик";
            case JOURNAL:
                return "Журнал сервера";
            case ABOUT:
                return "Как это работает";
            case STATS:
                return "Статистика";
            case APPEARANCE:
                return "Тема приложения";
            case VOICE:
                return "Голосовые сообщения";
            default:
                return "Reply AI";
        }
    }

    private void buildHome() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setGravity(16);
        ImageView imageView = new ImageView(this);
        imageView.setImageResource(R.drawable.ic_app);
        imageView.setImportantForAccessibility(2);
        linearLayout.addView(imageView, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout linearLayoutColumn = column();
        linearLayoutColumn.setPadding(dp(12), 0, 0, 0);
        TextView textViewLabel = label("Reply AI", 29, this.INK);
        textViewLabel.setTypeface(null, 1);
        linearLayoutColumn.addView(textViewLabel);
        linearLayoutColumn.addView(label("Ваш AI-автоответчик в Telegram", 13, this.MUTED));
        linearLayout.addView(linearLayoutColumn, new LinearLayout.LayoutParams(0, -2, 1.0f));
        add(this.content, linearLayout, 0);
        buildStatus(card("Сейчас"));
        navigation("1. Подключение к VPS", "Сервер, адрес и ключ доступа", Screen.CONNECTION);
        navigation("2. Telegram", "Вход в аккаунт и подключение", Screen.TELEGRAM);
        navigation("3. Нейросеть и текст", "AI API, оформление и проверка", Screen.AI);
        navigation("4. Личные чаты", "Выбор собеседников для автоответов", Screen.CHATS);
        navigation("5. Автоответчик", "Включить, остановить, проверить статус", Screen.CONTROL);
        navigation("6. Журнал сервера", "События и диагностика", Screen.JOURNAL);
        navigation("7. Как это работает", "О приложении и авторе", Screen.ABOUT);
        navigation("8. Статистика", "Ответы, голосовые и ошибки по дням", Screen.STATS);
        navigation("9. Тема приложения", "Светлая, тёмная или системная", Screen.APPEARANCE);
    }

    private void navigation(String str, String str2, final Screen screen) {
        SpannableString spannableString = new SpannableString(str + "\n" + str2);
        spannableString.setSpan(new StyleSpan(1), 0, str.length(), 33);
        spannableString.setSpan(new RelativeSizeSpan(0.78f), str.length() + 1, spannableString.length(), 33);
        spannableString.setSpan(new ForegroundColorSpan(this.MUTED), str.length() + 1, spannableString.length(), 33);
        Button button = button("", false);
        button.setText(spannableString);
        button.setTextColor(this.INK);
        button.setTextSize(16.0f);
        button.setGravity(8388627);
        button.setMinHeight(dp(78));
        button.setPadding(dp(18), dp(12), dp(14), dp(12));
        button.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_chevron, 0);
        button.setCompoundDrawablePadding(dp(8));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(this.colors.card);
        gradientDrawable.setCornerRadius(dp(18));
        gradientDrawable.setStroke(dp(1), this.colors.border);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(this.colors.soft), gradientDrawable, null));
        button.setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda91
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1714lambda$navigation$3$rureplyaiandroidMainActivity(screen, view);
            }
        });
        add(this.content, button, 11);
    }
    /* synthetic */ void m1714lambda$navigation$3$rureplyaiandroidMainActivity(Screen screen, View view) {
        showScreen(screen);
    }

    private void buildStatus(LinearLayout linearLayout) {
        TextView textViewLabel = label("Подключите сервер", 18, this.INK);
        this.statusText = textViewLabel;
        textViewLabel.setTypeface(null, 1);
        add(linearLayout, this.statusText, 6);
        TextView textViewLabel2 = label("Начните с раздела «Подключение к VPS».", 13, this.MUTED);
        this.detailText = textViewLabel2;
        add(linearLayout, textViewLabel2, 7);
    }

    private void buildAi() {
        LinearLayout linearLayoutCard = card("AI API и ответы");
        add(linearLayoutCard, label("Выберите нейросеть, задайте ей инструкцию и настройте внешний вид ответов.", 14, this.MUTED), 4);
        action(linearLayoutCard, "Настроить AI API", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda7
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1660lambda$buildAi$4$rureplyaiandroidMainActivity(view);
            }
        });
        action(linearLayoutCard, "Оформление ответов", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda8
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1661lambda$buildAi$5$rureplyaiandroidMainActivity(view);
            }
        });
        action(linearLayoutCard, "Голосовые сообщения", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda9
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1662lambda$buildAi$6$rureplyaiandroidMainActivity(view);
            }
        });
        action(linearLayoutCard, "Проверить AI без отправки в Telegram", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda10
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1663lambda$buildAi$7$rureplyaiandroidMainActivity(view);
            }
        });
    }
    /* synthetic */ void m1660lambda$buildAi$4$rureplyaiandroidMainActivity(View view) {
        api("GET", "/api/settings", null, new Result() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda69
            @Override // ru.replyai.android.MainActivity.Result
            public final void accept(JSONObject jSONObject) {
                MainActivity.this.aiDialog(jSONObject);
            }
        });
    }
    /* synthetic */ void m1661lambda$buildAi$5$rureplyaiandroidMainActivity(View view) {
        api("GET", "/api/settings", null, new Result() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda30
            @Override // ru.replyai.android.MainActivity.Result
            public final void accept(JSONObject jSONObject) {
                MainActivity.this.formatDialog(jSONObject);
            }
        });
    }
    /* synthetic */ void m1662lambda$buildAi$6$rureplyaiandroidMainActivity(View view) {
        showScreen(Screen.VOICE);
    }
    /* synthetic */ void m1663lambda$buildAi$7$rureplyaiandroidMainActivity(View view) {
        testDialog();
    }

    private void buildChats() {
        LinearLayout linearLayoutCard = card("Кому отвечать");
        add(linearLayoutCard, label("Выберите личные чаты, в которых нужны автоматические ответы. Изменить выбор можно в любой момент.", 15, this.MUTED), 4);
        action(linearLayoutCard, "Выбрать личные чаты", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda31
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1665lambda$buildChats$8$rureplyaiandroidMainActivity(view);
            }
        });
        add(linearLayoutCard, label("Собеседник может отключить автоответы в своём чате командой /ai_off.", 13, this.MUTED), 12);
    }
    /* synthetic */ void m1665lambda$buildChats$8$rureplyaiandroidMainActivity(View view) {
        chooseChats();
    }

    private void buildJournal() {
        LinearLayout linearLayoutCard = card("События и диагностика");
        add(linearLayoutCard, label("Последние 60 событий сервера. Тексты переписки в журнал не записываются.", 14, this.MUTED), 4);
        TextView textViewLabel = label("Ожидаем данные сервера…", 12, this.MUTED);
        this.journalState = textViewLabel;
        add(linearLayoutCard, textViewLabel, 10);
        action(linearLayoutCard, "Обновить журнал", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda45
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1679lambda$buildJournal$9$rureplyaiandroidMainActivity(view);
            }
        });
        action(linearLayoutCard, "Сохранить диагностику", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda46
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1676lambda$buildJournal$10$rureplyaiandroidMainActivity(view);
            }
        });
        action(linearLayoutCard, "Очистить журнал и отметки отправок", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda47
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1678lambda$buildJournal$12$rureplyaiandroidMainActivity(view);
            }
        });
        LinearLayout linearLayoutCard2 = card("Последние события");
        TextView textViewLabel2 = label("Подключите сервер, чтобы увидеть события.", 13, this.MUTED);
        this.logsText = textViewLabel2;
        textViewLabel2.setTextIsSelectable(true);
        add(linearLayoutCard2, this.logsText, 8);
    }
    /* synthetic */ void m1679lambda$buildJournal$9$rureplyaiandroidMainActivity(View view) {
        fetchStatus(true);
    }
    /* synthetic */ void m1676lambda$buildJournal$10$rureplyaiandroidMainActivity(View view) {
        startActivity(new Intent(this, (Class<?>) DiagnosticsActivity.class));
    }
    /* synthetic */ void m1677lambda$buildJournal$11$rureplyaiandroidMainActivity(JSONObject jSONObject) throws Exception {
        fetchStatus(false);
    }
    /* synthetic */ void m1678lambda$buildJournal$12$rureplyaiandroidMainActivity(View view) {
        api("POST", "/api/clear", new JSONObject(), new Result() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda14
            @Override // ru.replyai.android.MainActivity.Result
            public final void accept(JSONObject jSONObject) throws Exception {
                MainActivity.this.m1677lambda$buildJournal$11$rureplyaiandroidMainActivity(jSONObject);
            }
        });
    }

    private boolean supports27(JSONObject jSONObject) {
        try {
            String[] strArrSplit = jSONObject.optString("version").split("\\.");
            int i = Integer.parseInt(strArrSplit[0]);
            return i > 2 || (i == 2 && Integer.parseInt(strArrSplit[1]) >= 7);
        } catch (Exception unused) {
            return false;
        }
    }

    private void withServer27(final Runnable runnable) {
        JSONObject jSONObject = this.lastStatus;
        if (jSONObject == null || !supports27(jSONObject) || this.connectionUnavailable) {
            api("GET", "/api/status", null, new Result() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda27
                @Override // ru.replyai.android.MainActivity.Result
                public final void accept(JSONObject jSONObject2) throws Exception {
                    MainActivity.this.m1731lambda$withServer27$14$rureplyaiandroidMainActivity(runnable, jSONObject2);
                }
            });
        } else {
            runnable.run();
        }
    }
    /* synthetic */ void m1731lambda$withServer27$14$rureplyaiandroidMainActivity(Runnable runnable, JSONObject jSONObject) throws Exception {
        showStatus(jSONObject);
        if (supports27(jSONObject)) {
            runnable.run();
        } else {
            new AlertDialog.Builder(this).setTitle("Обновите сервер до 2.7").setMessage("Новые функции требуют обновления серверной части. Откройте раздел VPS и запустите «Установить сервер из приложения» для своего действующего сервера. Telegram и ключи сохраняются.").setPositiveButton("Открыть раздел VPS", new DialogInterface.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda20
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.m1730lambda$withServer27$13$rureplyaiandroidMainActivity(dialogInterface, i);
                }
            }).setNegativeButton("Позже", (DialogInterface.OnClickListener) null).show();
        }
    }
    /* synthetic */ void m1730lambda$withServer27$13$rureplyaiandroidMainActivity(DialogInterface dialogInterface, int i) {
        showScreen(Screen.CONNECTION);
    }

    private void buildAppearance() {
        LinearLayout linearLayoutCard = card("Выберите тему");
        add(linearLayoutCard, label("Оформление меняется во всех разделах приложения.", 15, this.MUTED), 6);
        RadioGroup radioGroup = new RadioGroup(this);
        final String[] strArr = {"system", "light", "dark"};
        String[] strArr2 = {"Как на телефоне", "Светлая", "Тёмная"};
        final int[] iArr = new int[3];
        int i = 0;
        for (int i2 = 0; i2 < 3; i2++) {
            RadioButton radioButton = new RadioButton(this);
            int iGenerateViewId = View.generateViewId();
            iArr[i2] = iGenerateViewId;
            radioButton.setId(iGenerateViewId);
            radioButton.setText(strArr2[i2]);
            radioButton.setTextColor(this.INK);
            radioButton.setTextSize(17.0f);
            radioButton.setMinHeight(dp(56));
            radioGroup.addView(radioButton);
            if (strArr[i2].equals(ThemeManager.choice(this))) {
                i = i2;
            }
        }
        radioGroup.check(iArr[i]);
        add(linearLayoutCard, radioGroup, 12);
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda29
            @Override // android.widget.RadioGroup.OnCheckedChangeListener
            public final void onCheckedChanged(RadioGroup radioGroup2, int i3) {
                MainActivity.this.m1664lambda$buildAppearance$15$rureplyaiandroidMainActivity(iArr, strArr, radioGroup2, i3);
            }
        });
    }
    /* synthetic */ void m1664lambda$buildAppearance$15$rureplyaiandroidMainActivity(int[] iArr, String[] strArr, RadioGroup radioGroup, int i) {
        for (int i2 = 0; i2 < 3; i2++) {
            if (iArr[i2] == i && !strArr[i2].equals(ThemeManager.choice(this))) {
                ThemeManager.save(this, strArr[i2]);
                recreate();
                return;
            }
        }
    }

    private void buildStats() {
        LinearLayout linearLayoutCard = card("Сегодня");
        TextView textViewLabel = label("Подключите VPS, чтобы загрузить статистику.", 16, this.INK);
        this.statsText = textViewLabel;
        add(linearLayoutCard, textViewLabel, 10);
        action(linearLayoutCard, "Обновить статистику", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda52
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1680lambda$buildStats$16$rureplyaiandroidMainActivity(view);
            }
        });
        LinearLayout linearLayoutCard2 = card("Ответы за 7 дней");
        this.statsChart = new StatsChartView(this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, dp(220));
        layoutParams.topMargin = dp(12);
        linearLayoutCard2.addView(this.statsChart, layoutParams);
        LinearLayout linearLayoutCard3 = card("Общие показатели");
        TextView textViewLabel2 = label("Подробная статистика собирается с обновления сервера до 2.7.", 14, this.MUTED);
        this.statsNote = textViewLabel2;
        add(linearLayoutCard3, textViewLabel2, 8);
        if (this.settings.url().isEmpty() || this.settings.token().isEmpty()) {
            return;
        }
        loadStatistics();
    }
    /* synthetic */ void m1680lambda$buildStats$16$rureplyaiandroidMainActivity(View view) {
        loadStatistics();
    }

    private void loadStatistics() {
        withServer27(new Runnable() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda53
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m1708lambda$loadStatistics$17$rureplyaiandroidMainActivity();
            }
        });
    }
    /* synthetic */ void m1708lambda$loadStatistics$17$rureplyaiandroidMainActivity() {
        api("GET", "/api/stats", null, new Result() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda77
            @Override // ru.replyai.android.MainActivity.Result
            public final void accept(JSONObject jSONObject) {
                MainActivity.this.showStatistics(jSONObject);
            }
        });
    }
    public void showStatistics(JSONObject jSONObject) {
        if (this.statsText == null || this.statsChart == null || this.statsNote == null) {
            return;
        }
        JSONObject jSONObjectOptJSONObject = jSONObject.optJSONObject("today");
        JSONObject jSONObjectOptJSONObject2 = jSONObject.optJSONObject("totals");
        if (jSONObjectOptJSONObject == null || jSONObjectOptJSONObject2 == null) {
            error("Не удалось прочитать статистику.");
            return;
        }
        this.statsText.setText("Отправлено ответов: " + jSONObjectOptJSONObject.optInt("replies") + "\nВходящих сообщений: " + jSONObjectOptJSONObject.optInt("received") + "\nРаспознано голосовых: " + jSONObjectOptJSONObject.optInt("voice") + "\nОбъединено сообщений: " + jSONObjectOptJSONObject.optInt("merged") + "\nОшибок запросов: " + jSONObjectOptJSONObject.optInt("errors"));
        String str = jSONObjectOptJSONObject2.optInt("replies") == 0 ? "—" : String.format(Locale.ROOT, "%.1f с", Double.valueOf(jSONObject.optLong("average_reply_ms") / 1000.0d));
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT);
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone(jSONObject.optString("timezone", "Europe/Moscow")));
        this.statsNote.setText("Всего отправлено: " + jSONObject.optInt("lifetime_sent") + "\nСреднее время ответа: " + str + "\nОбъединено за всё время: " + jSONObjectOptJSONObject2.optInt("merged") + "\nРаспознано голосовых: " + jSONObjectOptJSONObject2.optInt("voice") + "\nОшибок запросов: " + jSONObjectOptJSONObject2.optInt("errors") + "\n\nПодробные данные с " + simpleDateFormat.format(new Date(jSONObject.optLong("since") * 1000)) + ".\nЧасовой пояс: " + jSONObject.optString("timezone") + "\nОбъединено — число дополнительных сообщений в общих ответах.");
        this.statsChart.setDays(jSONObject.optJSONArray("series"));
    }
    public void batchDialog(JSONObject jSONObject) {
        LinearLayout linearLayoutDialogBox = dialogBox();
        add(linearLayoutDialogBox, label("Сообщения одного собеседника, пришедшие подряд, получат один общий ответ. Пауза отсчитывается от последнего сообщения; общее ожидание — до 30 секунд.", 14, this.MUTED), 0);
        final EditText editTextField = field(linearLayoutDialogBox, "Сколько секунд ждать", String.valueOf(jSONObject.optInt("batch_seconds", 5)), false, 2);
        editTextField.setInputType(2);
        add(linearLayoutDialogBox, label("5 секунд — обычный режим. 0 — отвечать на каждое сообщение отдельно. Настройка действует для новых сообщений.", 13, this.MUTED), 12);
        final AlertDialog alertDialogDialog = dialog("Объединение сообщений", linearLayoutDialogBox, "Сохранить");
        alertDialogDialog.setOnShowListener(new DialogInterface.OnShowListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda83
            @Override // android.content.DialogInterface.OnShowListener
            public final void onShow(DialogInterface dialogInterface) {
                MainActivity.this.m1658lambda$batchDialog$20$rureplyaiandroidMainActivity(alertDialogDialog, editTextField, dialogInterface);
            }
        });
        alertDialogDialog.show();
    }
    /* synthetic */ void m1656lambda$batchDialog$18$rureplyaiandroidMainActivity(JSONObject jSONObject) throws Exception {
        message(jSONObject.optString("message"));
    }
    /* synthetic */ void m1657lambda$batchDialog$19$rureplyaiandroidMainActivity(EditText editText, AlertDialog alertDialog, View view) {
        try {
            int i = Integer.parseInt(editText.getText().toString());
            if (i < 0 || i > 30) {
                throw new Exception();
            }
            JSONObject jSONObjectPut = new JSONObject().put("batch_seconds", i);
            alertDialog.dismiss();
            api("PUT", "/api/behavior", jSONObjectPut, new Result() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda23
                @Override // ru.replyai.android.MainActivity.Result
                public final void accept(JSONObject jSONObject) throws Exception {
                    MainActivity.this.m1656lambda$batchDialog$18$rureplyaiandroidMainActivity(jSONObject);
                }
            });
        } catch (Exception unused) {
            error("Введите число от 0 до 30 секунд.");
        }
    }
    /* synthetic */ void m1658lambda$batchDialog$20$rureplyaiandroidMainActivity(final AlertDialog alertDialog, final EditText editText, DialogInterface dialogInterface) {
        alertDialog.getButton(-1).setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda35
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1657lambda$batchDialog$19$rureplyaiandroidMainActivity(editText, alertDialog, view);
            }
        });
    }
    public void quietDialog(JSONObject jSONObject) {
        LinearLayout linearLayoutDialogBox = dialogBox();
        add(linearLayoutDialogBox, label("В выбранное время автоответы не отправляются. Ночные сообщения пропускаются; утром работа возобновляется с новых сообщений. При изменении расписания ожидающие ответы отменяются.", 14, this.MUTED), 0);
        final CheckBox checkBox = new CheckBox(this);
        checkBox.setText("Включить тихие часы");
        checkBox.setTextColor(this.INK);
        checkBox.setChecked(jSONObject.optBoolean("quiet_enabled"));
        add(linearLayoutDialogBox, checkBox, 12);
        final EditText editTextField = field(linearLayoutDialogBox, "Начало (ЧЧ:ММ)", jSONObject.optString("quiet_start", "23:00"), false, 5);
        final EditText editTextField2 = field(linearLayoutDialogBox, "Конец (ЧЧ:ММ)", jSONObject.optString("quiet_end", "08:00"), false, 5);
        editTextField.setInputType(36);
        editTextField2.setInputType(36);
        add(linearLayoutDialogBox, label("Часовой пояс", 13, this.MUTED), 12);
        final Spinner spinner = new Spinner(this);
        final ArrayList arrayList = new ArrayList(Arrays.asList("Europe/Moscow", "Europe/Kaliningrad", "Europe/Samara", "Asia/Yekaterinburg", "Asia/Omsk", "Asia/Novosibirsk", "Asia/Krasnoyarsk", "Asia/Irkutsk", "Asia/Vladivostok", "Asia/Tokyo"));
        ArrayList arrayList2 = new ArrayList(Arrays.asList("Москва · UTC+3", "Калининград · UTC+2", "Самара · UTC+4", "Екатеринбург · UTC+5", "Омск · UTC+6", "Новосибирск · UTC+7", "Красноярск · UTC+7", "Иркутск · UTC+8", "Владивосток · UTC+10", "Токио · UTC+9"));
        String strOptString = jSONObject.optString("quiet_zone", "Europe/Moscow");
        if (!arrayList.contains(strOptString)) {
            arrayList.add(strOptString);
            arrayList2.add(strOptString);
        }
        spinner.setAdapter((SpinnerAdapter) new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayList2));
        spinner.setSelection(arrayList.indexOf(strOptString));
        add(linearLayoutDialogBox, spinner, 6);
        final AlertDialog alertDialogDialog = dialog("Тихие часы", linearLayoutDialogBox, "Сохранить");
        alertDialogDialog.setOnShowListener(new DialogInterface.OnShowListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda38
            @Override // android.content.DialogInterface.OnShowListener
            public final void onShow(DialogInterface dialogInterface) {
                MainActivity.this.m1717lambda$quietDialog$23$rureplyaiandroidMainActivity(alertDialogDialog, editTextField, editTextField2, checkBox, arrayList, spinner, dialogInterface);
            }
        });
        alertDialogDialog.show();
    }
    /* synthetic */ void m1715lambda$quietDialog$21$rureplyaiandroidMainActivity(JSONObject jSONObject) throws Exception {
        message(jSONObject.optString("message"));
        fetchStatus(false);
    }
    /* synthetic */ void m1716lambda$quietDialog$22$rureplyaiandroidMainActivity(EditText editText, EditText editText2, CheckBox checkBox, ArrayList arrayList, Spinner spinner, AlertDialog alertDialog, View view) {
        try {
            String string = editText.getText().toString();
            String string2 = editText2.getText().toString();
            if (!string.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]") || !string2.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]") || (checkBox.isChecked() && string.equals(string2))) {
                throw new Exception();
            }
            JSONObject jSONObjectPut = new JSONObject().put("quiet_enabled", checkBox.isChecked()).put("quiet_start", string).put("quiet_end", string2).put("quiet_zone", arrayList.get(spinner.getSelectedItemPosition()));
            alertDialog.dismiss();
            api("PUT", "/api/behavior", jSONObjectPut, new Result() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda36
                @Override // ru.replyai.android.MainActivity.Result
                public final void accept(JSONObject jSONObject) throws Exception {
                    MainActivity.this.m1715lambda$quietDialog$21$rureplyaiandroidMainActivity(jSONObject);
                }
            });
        } catch (Exception unused) {
            error("Укажите разное время начала и конца в формате ЧЧ:ММ.");
        }
    }
    /* synthetic */ void m1717lambda$quietDialog$23$rureplyaiandroidMainActivity(final AlertDialog alertDialog, final EditText editText, final EditText editText2, final CheckBox checkBox, final ArrayList arrayList, final Spinner spinner, DialogInterface dialogInterface) {
        alertDialog.getButton(-1).setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda61
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1716lambda$quietDialog$22$rureplyaiandroidMainActivity(editText, editText2, checkBox, arrayList, spinner, alertDialog, view);
            }
        });
    }

    private void buildVoice() {
        LinearLayout linearLayoutCard = card("Расшифровка и ответы");
        add(linearLayoutCard, label("Голосовые выбранных чатов распознаются через Groq Whisper. Нейросеть отвечает на их содержание текстом.", 15, this.MUTED), 6);
        TextView textViewLabel = label("Подключите сервер 2.7, чтобы настроить распознавание.", 14, this.TEAL);
        this.voiceState = textViewLabel;
        add(linearLayoutCard, textViewLabel, 12);
        action(linearLayoutCard, "Настроить голосовые", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda17
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1692lambda$buildVoice$25$rureplyaiandroidMainActivity(view);
            }
        });
        action(linearLayoutCard, "Обновить расшифровки", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda18
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1693lambda$buildVoice$26$rureplyaiandroidMainActivity(view);
            }
        });
        action(linearLayoutCard, "Очистить расшифровки", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda19
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1696lambda$buildVoice$29$rureplyaiandroidMainActivity(view);
            }
        });
        LinearLayout linearLayoutCard2 = card("Последние расшифровки");
        add(linearLayoutCard2, label("Сохраняются последние 10 текстов на вашем VPS. Аудиофайлы на диск не записываются.", 13, this.MUTED), 8);
        LinearLayout linearLayoutColumn = column();
        this.transcriptsList = linearLayoutColumn;
        add(linearLayoutCard2, linearLayoutColumn, 8);
        add(this.transcriptsList, label("Расшифровок пока нет.", 14, this.MUTED), 4);
        if (this.settings.url().isEmpty() || this.settings.token().isEmpty()) {
            return;
        }
        loadVoice();
    }
    /* synthetic */ void m1691lambda$buildVoice$24$rureplyaiandroidMainActivity() {
        api("GET", "/api/voice", null, new Result() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda49
            @Override // ru.replyai.android.MainActivity.Result
            public final void accept(JSONObject jSONObject) {
                MainActivity.this.voiceDialog(jSONObject);
            }
        });
    }
    /* synthetic */ void m1692lambda$buildVoice$25$rureplyaiandroidMainActivity(View view) {
        withServer27(new Runnable() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda48
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m1691lambda$buildVoice$24$rureplyaiandroidMainActivity();
            }
        });
    }
    /* synthetic */ void m1693lambda$buildVoice$26$rureplyaiandroidMainActivity(View view) {
        loadVoice();
    }
    /* synthetic */ void m1694lambda$buildVoice$27$rureplyaiandroidMainActivity(JSONObject jSONObject) throws Exception {
        message(jSONObject.optString("message"));
        loadVoice();
    }
    /* synthetic */ void m1695lambda$buildVoice$28$rureplyaiandroidMainActivity() {
        api("POST", "/api/voice/clear", new JSONObject(), new Result() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda41
            @Override // ru.replyai.android.MainActivity.Result
            public final void accept(JSONObject jSONObject) throws Exception {
                MainActivity.this.m1694lambda$buildVoice$27$rureplyaiandroidMainActivity(jSONObject);
            }
        });
    }
    /* synthetic */ void m1696lambda$buildVoice$29$rureplyaiandroidMainActivity(View view) {
        withServer27(new Runnable() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda54
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m1695lambda$buildVoice$28$rureplyaiandroidMainActivity();
            }
        });
    }

    private void loadVoice() {
        withServer27(new Runnable() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda55
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m1709lambda$loadVoice$30$rureplyaiandroidMainActivity();
            }
        });
    }
    /* synthetic */ void m1709lambda$loadVoice$30$rureplyaiandroidMainActivity() {
        api("GET", "/api/voice", null, new Result() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda80
            @Override // ru.replyai.android.MainActivity.Result
            public final void accept(JSONObject jSONObject) {
                MainActivity.this.showVoice(jSONObject);
            }
        });
    }
    public void showVoice(JSONObject jSONObject) {
        TextView textView = this.voiceState;
        if (textView == null || this.transcriptsList == null) {
            return;
        }
        textView.setText(jSONObject.optBoolean("enabled") ? jSONObject.optBoolean("available") ? "Распознавание включено · Groq Whisper" : "Укажите ключ Groq в настройках голосовых" : "Распознавание выключено");
        this.transcriptsList.removeAllViews();
        JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("transcripts");
        if (jSONArrayOptJSONArray == null || jSONArrayOptJSONArray.length() == 0) {
            add(this.transcriptsList, label("Новые голосовые выбранных чатов появятся здесь после распознавания.", 14, this.MUTED), 4);
            return;
        }
        for (int i = 0; i < Math.min(10, jSONArrayOptJSONArray.length()); i++) {
            JSONObject jSONObjectOptJSONObject = jSONArrayOptJSONArray.optJSONObject(i);
            if (jSONObjectOptJSONObject != null) {
                final String strOptString = jSONObjectOptJSONObject.optString("text");
                String strReplace = strOptString.replace('\n', ' ');
                if (strReplace.length() > 90) {
                    strReplace = strReplace.substring(0, 90) + "…";
                }
                action(this.transcriptsList, new SimpleDateFormat("dd.MM HH:mm", Locale.ROOT).format(new Date(jSONObjectOptJSONObject.optLong("at") * 1000)) + "\n" + strReplace, new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda37
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        MainActivity.this.m1720lambda$showVoice$31$rureplyaiandroidMainActivity(strOptString, view);
                    }
                });
            }
        }
    }
    /* synthetic */ void m1720lambda$showVoice$31$rureplyaiandroidMainActivity(String str, View view) {
        LinearLayout linearLayoutDialogBox = dialogBox();
        TextView textViewLabel = label(str, 16, this.INK);
        textViewLabel.setTextIsSelectable(true);
        add(linearLayoutDialogBox, textViewLabel, 8);
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(linearLayoutDialogBox);
        new AlertDialog.Builder(this).setTitle("Расшифровка голосового").setView(scrollView).setPositiveButton("Закрыть", (DialogInterface.OnClickListener) null).show();
    }
    public void voiceDialog(JSONObject jSONObject) {
        LinearLayout linearLayoutDialogBox = dialogBox();
        add(linearLayoutDialogBox, label("Используется Groq Whisper. Можно взять ключ Groq из настроек AI или ввести отдельный. Доступность и квоты зависят от аккаунта провайдера.", 14, this.MUTED), 0);
        final CheckBox checkBox = new CheckBox(this);
        checkBox.setText("Распознавать голосовые и отвечать на них");
        checkBox.setTextColor(this.INK);
        checkBox.setChecked(jSONObject.optBoolean("enabled", true));
        add(linearLayoutDialogBox, checkBox, 12);
        final CheckBox checkBox2 = new CheckBox(this);
        checkBox2.setText("Использовать ключ Groq из настроек AI");
        checkBox2.setTextColor(this.INK);
        checkBox2.setChecked(jSONObject.optBoolean("use_chat_key", true) && jSONObject.optBoolean("can_use_chat_key"));
        add(linearLayoutDialogBox, checkBox2, 8);
        final EditText editTextField = field(linearLayoutDialogBox, "Отдельный API-ключ Groq", "", true, 2048);
        editTextField.setHint(jSONObject.optBoolean("has_key") ? "Сохранён. Пусто — оставить прежний" : "gsk_…");
        editTextField.setEnabled(!checkBox2.isChecked());
        checkBox2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda12
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                editTextField.setEnabled(!z);
            }
        });
        add(linearLayoutDialogBox, label("Модель распознавания", 13, this.MUTED), 12);
        final Spinner spinner = new Spinner(this);
        final String[] strArr = {"whisper-large-v3-turbo", "whisper-large-v3"};
        spinner.setAdapter((SpinnerAdapter) new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Whisper Turbo", "Whisper Large V3"}));
        spinner.setSelection(jSONObject.optString("model").equals(strArr[1]) ? 1 : 0);
        add(linearLayoutDialogBox, spinner, 6);
        add(linearLayoutDialogBox, label("Язык голосовых", 13, this.MUTED), 12);
        final Spinner spinner2 = new Spinner(this);
        final String[] strArr2 = {"", "ru", "en", "kk"};
        spinner2.setAdapter((SpinnerAdapter) new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Определять автоматически", "Русский", "Английский", "Казахский"}));
        for (int i = 0; i < 4; i++) {
            if (strArr2[i].equals(jSONObject.optString("language"))) {
                spinner2.setSelection(i);
            }
        }
        add(linearLayoutDialogBox, spinner2, 6);
        final EditText editTextField2 = field(linearLayoutDialogBox, "Максимальная длительность, секунд", String.valueOf(jSONObject.optInt("max_seconds", 300)), false, 3);
        editTextField2.setInputType(2);
        add(linearLayoutDialogBox, label("От 30 до 600 секунд. Размер одного голосового — до 20 МБ.", 13, this.MUTED), 8);
        final AlertDialog alertDialogDialog = dialog("Голосовые сообщения", linearLayoutDialogBox, "Сохранить");
        alertDialogDialog.setOnShowListener(new DialogInterface.OnShowListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda13
            @Override // android.content.DialogInterface.OnShowListener
            public final void onShow(DialogInterface dialogInterface) {
                MainActivity.this.m1729lambda$voiceDialog$35$rureplyaiandroidMainActivity(alertDialogDialog, editTextField2, checkBox, checkBox2, editTextField, strArr, spinner, strArr2, spinner2, dialogInterface);
            }
        });
        alertDialogDialog.show();
    }
    /* synthetic */ void m1727lambda$voiceDialog$33$rureplyaiandroidMainActivity(JSONObject jSONObject) throws Exception {
        message(jSONObject.optString("message"));
        loadVoice();
    }
    /* synthetic */ void m1728lambda$voiceDialog$34$rureplyaiandroidMainActivity(EditText editText, CheckBox checkBox, CheckBox checkBox2, EditText editText2, String[] strArr, Spinner spinner, String[] strArr2, Spinner spinner2, AlertDialog alertDialog, View view) {
        try {
            int i = Integer.parseInt(editText.getText().toString());
            if (i < 30 || i > 600) {
                throw new Exception();
            }
            JSONObject jSONObjectPut = new JSONObject().put("enabled", checkBox.isChecked()).put("use_chat_key", checkBox2.isChecked()).put("key", editText2.getText().toString().trim()).put("model", strArr[spinner.getSelectedItemPosition()]).put("language", strArr2[spinner2.getSelectedItemPosition()]).put("max_seconds", i);
            editText2.setText("");
            alertDialog.dismiss();
            api("PUT", "/api/voice", jSONObjectPut, new Result() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda92
                @Override // ru.replyai.android.MainActivity.Result
                public final void accept(JSONObject jSONObject) throws Exception {
                    MainActivity.this.m1727lambda$voiceDialog$33$rureplyaiandroidMainActivity(jSONObject);
                }
            });
        } catch (Exception unused) {
            error("Проверьте поля. Длительность — от 30 до 600 секунд.");
        }
    }
    /* synthetic */ void m1729lambda$voiceDialog$35$rureplyaiandroidMainActivity(final AlertDialog alertDialog, final EditText editText, final CheckBox checkBox, final CheckBox checkBox2, final EditText editText2, final String[] strArr, final Spinner spinner, final String[] strArr2, final Spinner spinner2, DialogInterface dialogInterface) {
        alertDialog.getButton(-1).setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda44
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1728lambda$voiceDialog$34$rureplyaiandroidMainActivity(editText, checkBox, checkBox2, editText2, strArr, spinner, strArr2, spinner2, alertDialog, view);
            }
        });
    }

    private void buildAbout() {
        LinearLayout linearLayoutCard = card("Что такое Reply AI");
        add(linearLayoutCard, label("AI-автоответчик для личных чатов Telegram. Получает новое сообщение и отвечает с помощью выбранной нейросети.", 16, this.INK), 8);
        add(linearLayoutCard, label("Помогает поддерживать переписку, когда вы заняты. Вы выбираете чаты, инструкцию для AI и оформление ответов.", 15, this.MUTED), 14);
        add(linearLayoutCard, label("Работает на вашем VPS: приложение можно закрыть, а телефон — заблокировать. Нужны работающий сервер и доступ к AI API.", 15, this.MUTED), 14);
        LinearLayout linearLayoutCard2 = card("Автор приложения");
        TextView textViewLabel = label("@stytam", 22, this.TEAL);
        textViewLabel.setTypeface(null, 1);
        add(linearLayoutCard2, textViewLabel, 8);
        action(linearLayoutCard2, "Открыть профиль автора", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda89
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1659lambda$buildAbout$36$rureplyaiandroidMainActivity(view);
            }
        });
    }
    /* synthetic */ void m1659lambda$buildAbout$36$rureplyaiandroidMainActivity(View view) {
        startActivity(new Intent("android.intent.action.VIEW", Uri.parse("https://t.me/stytam")));
    }
    public void backToMenu() {
        if (this.screen == Screen.HOME) {
            finish();
        } else {
            showScreen(Screen.HOME);
        }
    }

    @Override // android.app.Activity
    public void onBackPressed() {
        backToMenu();
    }

    @Override // android.app.Activity
    protected void onSaveInstanceState(Bundle bundle) {
        bundle.putString("section", this.screen.name());
        super.onSaveInstanceState(bundle);
    }

    private void buildConnection() {
        LinearLayout linearLayoutCard = card("Сервер и доступ");
        add(linearLayoutCard, label("Новый VPS можно настроить прямо здесь. Если сервер уже установлен, укажите его HTTPS адрес и ключ управления.", 14, this.MUTED), 4);
        action(linearLayoutCard, "Установить сервер из приложения", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda85
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1666lambda$buildConnection$37$rureplyaiandroidMainActivity(view);
            }
        });
        EditText editTextField = field(linearLayoutCard, "Адрес сервера", this.settings.url(), false, 2048);
        this.urlField = editTextField;
        editTextField.setInputType(17);
        this.tokenField = field(linearLayoutCard, "Ключ управления сервером", "", true, 256);
        if (!this.settings.token().isEmpty()) {
            this.tokenField.setHint("Сохранён. Пусто — оставить прежний");
        }
        action(linearLayoutCard, "Сохранить подключение", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda86
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1667lambda$buildConnection$38$rureplyaiandroidMainActivity(view);
            }
        });
        action(linearLayoutCard, "Сохранить диагностику", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda87
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1668lambda$buildConnection$39$rureplyaiandroidMainActivity(view);
            }
        });
    }
    /* synthetic */ void m1666lambda$buildConnection$37$rureplyaiandroidMainActivity(View view) {
        startActivityForResult(new Intent(this, (Class<?>) ServerSetupActivity.class), 42);
    }
    /* synthetic */ void m1667lambda$buildConnection$38$rureplyaiandroidMainActivity(View view) {
        try {
            String strNormalizeUrl = RemoteClient.normalizeUrl(this.urlField.getText().toString());
            String strTrim = this.tokenField.getText().toString().trim();
            if (strTrim.isEmpty()) {
                if (!strNormalizeUrl.equals(this.settings.url())) {
                    throw new Exception("Для нового адреса введите ключ управления заново.");
                }
                strTrim = this.settings.token();
            }
            if (!strNormalizeUrl.equals(this.settings.url()) && DiagnosticLog.get() != null) {
                DiagnosticLog.get().clearServerCache();
            }
            this.settings.save(strNormalizeUrl, strTrim);
            this.observedSettingsUrl = this.settings.url();
            this.connectionGeneration++;
            this.polling = false;
            this.tokenField.setText("");
            this.tokenField.setHint("Сохранён. Пусто — оставить прежний");
            fetchStatus(true);
        } catch (Exception e) {
            error(e.getMessage());
        }
    }
    /* synthetic */ void m1668lambda$buildConnection$39$rureplyaiandroidMainActivity(View view) {
        startActivity(new Intent(this, (Class<?>) DiagnosticsActivity.class));
    }

    private void buildControls() {
        LinearLayout linearLayoutCard = card("Управление ответами");
        buildStatus(linearLayoutCard);
        action(linearLayoutCard, "Объединение сообщений", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda71
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1670lambda$buildControls$41$rureplyaiandroidMainActivity(view);
            }
        });
        action(linearLayoutCard, "Тихие часы", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda72
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1672lambda$buildControls$43$rureplyaiandroidMainActivity(view);
            }
        });
        Button button = button("Включить автоответы", true);
        button.setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda73
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1673lambda$buildControls$44$rureplyaiandroidMainActivity(view);
            }
        });
        add(linearLayoutCard, button, 14);
        action(linearLayoutCard, "Остановить автоответы", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda74
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1674lambda$buildControls$45$rureplyaiandroidMainActivity(view);
            }
        });
        add(linearLayoutCard, label("Остановка отправляет команду серверу. При отсутствии связи команда не считается выполненной.", 12, this.MUTED), 8);
        action(linearLayoutCard, "Обновить состояние", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda75
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1675lambda$buildControls$46$rureplyaiandroidMainActivity(view);
            }
        });
    }
    /* synthetic */ void m1669lambda$buildControls$40$rureplyaiandroidMainActivity() {
        api("GET", "/api/behavior", null, new Result() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda42
            @Override // ru.replyai.android.MainActivity.Result
            public final void accept(JSONObject jSONObject) {
                MainActivity.this.batchDialog(jSONObject);
            }
        });
    }
    /* synthetic */ void m1670lambda$buildControls$41$rureplyaiandroidMainActivity(View view) {
        withServer27(new Runnable() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda26
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m1669lambda$buildControls$40$rureplyaiandroidMainActivity();
            }
        });
    }
    /* synthetic */ void m1671lambda$buildControls$42$rureplyaiandroidMainActivity() {
        api("GET", "/api/behavior", null, new Result() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda6
            @Override // ru.replyai.android.MainActivity.Result
            public final void accept(JSONObject jSONObject) {
                MainActivity.this.quietDialog(jSONObject);
            }
        });
    }
    /* synthetic */ void m1672lambda$buildControls$43$rureplyaiandroidMainActivity(View view) {
        withServer27(new Runnable() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda58
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m1671lambda$buildControls$42$rureplyaiandroidMainActivity();
            }
        });
    }
    /* synthetic */ void m1673lambda$buildControls$44$rureplyaiandroidMainActivity(View view) {
        try {
            api("POST", "/api/control", new JSONObject().put("enabled", true), new MainActivity$$ExternalSyntheticLambda2(this));
        } catch (Exception unused) {
            error("Не удалось создать запрос.");
        }
    }
    /* synthetic */ void m1674lambda$buildControls$45$rureplyaiandroidMainActivity(View view) {
        try {
            api("POST", "/api/control", new JSONObject().put("enabled", false), new MainActivity$$ExternalSyntheticLambda2(this));
        } catch (Exception unused) {
            error("Не удалось создать запрос.");
        }
    }
    /* synthetic */ void m1675lambda$buildControls$46$rureplyaiandroidMainActivity(View view) {
        fetchStatus(true);
    }

    private void buildTelegram() {
        LinearLayout linearLayoutCard = card("Личный аккаунт");
        buildStatus(linearLayoutCard);
        action(linearLayoutCard, "Войти в личный аккаунт Telegram", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1681lambda$buildTelegram$47$rureplyaiandroidMainActivity(view);
            }
        });
        action(linearLayoutCard, "Войти через QR — без кода", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda11
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1685lambda$buildTelegram$51$rureplyaiandroidMainActivity(view);
            }
        });
        action(linearLayoutCard, "Ввести код или облачный пароль", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda22
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1687lambda$buildTelegram$53$rureplyaiandroidMainActivity(view);
            }
        });
        action(linearLayoutCard, "Отключить аккаунт Telegram", new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda33
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1690lambda$buildTelegram$56$rureplyaiandroidMainActivity(view);
            }
        });
    }
    /* synthetic */ void m1681lambda$buildTelegram$47$rureplyaiandroidMainActivity(View view) {
        telegramLogin();
    }
    /* synthetic */ void m1685lambda$buildTelegram$51$rureplyaiandroidMainActivity(View view) {
        api("GET", "/api/telegram/qr", null, new Result() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda60
            @Override // ru.replyai.android.MainActivity.Result
            public final void accept(JSONObject jSONObject) throws Exception {
                MainActivity.this.m1684lambda$buildTelegram$50$rureplyaiandroidMainActivity(jSONObject);
            }
        });
    }
    /* synthetic */ void m1684lambda$buildTelegram$50$rureplyaiandroidMainActivity(JSONObject jSONObject) throws Exception {
        if (jSONObject.optString("stage").equals("authorized")) {
            qrNext(jSONObject);
        } else if (jSONObject.optBoolean("has_credentials")) {
            new AlertDialog.Builder(this).setTitle("Вход через QR").setMessage("Использовать данные последнего входа? Номер заканчивается на " + jSONObject.optString("phone_last4") + ". На втором устройстве откроется QR; подтвердите его в Telegram именно этого аккаунта.").setPositiveButton("Создать QR", new DialogInterface.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda56
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.m1682lambda$buildTelegram$48$rureplyaiandroidMainActivity(dialogInterface, i);
                }
            }).setNeutralButton("Изменить данные", new DialogInterface.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda57
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.m1683lambda$buildTelegram$49$rureplyaiandroidMainActivity(dialogInterface, i);
                }
            }).setNegativeButton("Отмена", (DialogInterface.OnClickListener) null).show();
        } else {
            telegramLogin(true);
        }
    }
    /* synthetic */ void m1682lambda$buildTelegram$48$rureplyaiandroidMainActivity(DialogInterface dialogInterface, int i) {
        api("POST", "/api/telegram/qr/start", null, new MainActivity$$ExternalSyntheticLambda88(this));
    }
    /* synthetic */ void m1683lambda$buildTelegram$49$rureplyaiandroidMainActivity(DialogInterface dialogInterface, int i) {
        telegramLogin(true);
    }
    /* synthetic */ void m1687lambda$buildTelegram$53$rureplyaiandroidMainActivity(View view) {
        api("GET", "/api/status", null, new Result() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda59
            @Override // ru.replyai.android.MainActivity.Result
            public final void accept(JSONObject jSONObject) throws Exception {
                MainActivity.this.m1686lambda$buildTelegram$52$rureplyaiandroidMainActivity(jSONObject);
            }
        });
    }
    /* synthetic */ void m1686lambda$buildTelegram$52$rureplyaiandroidMainActivity(JSONObject jSONObject) throws Exception {
        JSONObject jSONObjectOptJSONObject = jSONObject.optJSONObject("login");
        if (jSONObjectOptJSONObject != null) {
            loginNext(jSONObjectOptJSONObject);
            return;
        }
        String strOptString = jSONObject.optString("stage");
        if (strOptString.equals("code") || strOptString.equals("password")) {
            loginNext(new JSONObject().put("stage", strOptString).put("message", strOptString.equals("code") ? "Введите код Telegram." : "Введите облачный пароль Telegram."));
        } else {
            message("Сначала начните вход кнопкой выше. Если время кода истекло, запросите новый код.");
        }
    }
    /* synthetic */ void m1690lambda$buildTelegram$56$rureplyaiandroidMainActivity(View view) {
        new AlertDialog.Builder(this).setTitle("Завершить серверный сеанс?").setMessage("Автоответчик остановится, сеанс Telegram на сервере будет завершён. Для повторного подключения потребуется вход.").setNegativeButton("Отмена", (DialogInterface.OnClickListener) null).setPositiveButton("Отключить", new DialogInterface.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda39
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.m1689lambda$buildTelegram$55$rureplyaiandroidMainActivity(dialogInterface, i);
            }
        }).show();
    }
    /* synthetic */ void m1688lambda$buildTelegram$54$rureplyaiandroidMainActivity(JSONObject jSONObject) throws Exception {
        message(jSONObject.optString("message"));
        fetchStatus(false);
    }
    /* synthetic */ void m1689lambda$buildTelegram$55$rureplyaiandroidMainActivity(DialogInterface dialogInterface, int i) {
        api("POST", "/api/telegram/logout", new JSONObject(), new Result() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda76
            @Override // ru.replyai.android.MainActivity.Result
            public final void accept(JSONObject jSONObject) throws Exception {
                MainActivity.this.m1688lambda$buildTelegram$54$rureplyaiandroidMainActivity(jSONObject);
            }
        });
    }

    private void telegramLogin() {
        telegramLogin(false);
    }
    public void qrNext(JSONObject jSONObject) {
        if (jSONObject.optString("stage").equals("authorized")) {
            loginNext(jSONObject);
        } else {
            startActivity(new Intent(this, (Class<?>) QrLoginActivity.class));
        }
    }

    private void telegramLogin(final boolean z) {
        LinearLayout linearLayoutDialogBox = dialogBox();
        add(linearLayoutDialogBox, label("Получите собственные api_id и api_hash в API Development Tools на my.telegram.org. Код и облачный пароль вводите только здесь после подключения к своему серверу.", 14, this.MUTED), 0);
        Button button = button("Открыть my.telegram.org", false);
        button.setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda50
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1721lambda$telegramLogin$57$rureplyaiandroidMainActivity(view);
            }
        });
        add(linearLayoutDialogBox, button, 4);
        final EditText editTextField = field(linearLayoutDialogBox, "api_id", "", false, 12);
        editTextField.setInputType(2);
        final EditText editTextField2 = field(linearLayoutDialogBox, "api_hash", "", true, 32);
        final EditText editTextField3 = field(linearLayoutDialogBox, "Телефон, например +79991234567", "", false, 16);
        editTextField3.setInputType(3);
        final CheckBox checkBox = new CheckBox(this);
        checkBox.setText("Подключаю свой аккаунт. Его сеанс будет храниться на моём VPS и сможет отправлять выбранные автоответы.");
        checkBox.setTextSize(14.0f);
        add(linearLayoutDialogBox, checkBox, 12);
        if (z) {
            add(linearLayoutDialogBox, label("Телефон нужен для проверки, что QR подтверждён именно вашим аккаунтом. Код запрашиваться не будет.", 14, this.MUTED), 8);
        }
        final AlertDialog alertDialogDialog = dialog(z ? "Вход через QR" : "Вход в Telegram", linearLayoutDialogBox, z ? "Создать QR" : "Получить код");
        alertDialogDialog.setOnShowListener(new DialogInterface.OnShowListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda51
            @Override // android.content.DialogInterface.OnShowListener
            public final void onShow(DialogInterface dialogInterface) {
                MainActivity.this.m1723lambda$telegramLogin$59$rureplyaiandroidMainActivity(alertDialogDialog, checkBox, editTextField2, editTextField3, editTextField, z, dialogInterface);
            }
        });
        alertDialogDialog.show();
    }
    /* synthetic */ void m1721lambda$telegramLogin$57$rureplyaiandroidMainActivity(View view) {
        startActivity(new Intent("android.intent.action.VIEW", Uri.parse("https://my.telegram.org")));
    }
    /* synthetic */ void m1723lambda$telegramLogin$59$rureplyaiandroidMainActivity(final AlertDialog alertDialog, final CheckBox checkBox, final EditText editText, final EditText editText2, final EditText editText3, final boolean z, DialogInterface dialogInterface) {
        alertDialog.getButton(-1).setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda66
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1722lambda$telegramLogin$58$rureplyaiandroidMainActivity(checkBox, editText, editText2, editText3, alertDialog, z, view);
            }
        });
    }
    /* synthetic */ void m1722lambda$telegramLogin$58$rureplyaiandroidMainActivity(CheckBox checkBox, EditText editText, EditText editText2, EditText editText3, AlertDialog alertDialog, boolean z, View view) {
        if (!checkBox.isChecked()) {
            error("Подтвердите подключение собственного аккаунта.");
            return;
        }
        try {
            String strTrim = editText.getText().toString().trim();
            String strTrim2 = editText2.getText().toString().trim();
            long j = Long.parseLong(editText3.getText().toString());
            if (j <= 0 || !strTrim.matches("[a-fA-F0-9]{32}") || !strTrim2.matches("\\+[0-9]{7,15}")) {
                throw new Exception("Проверьте api_id, api_hash и номер телефона.");
            }
            JSONObject jSONObjectPut = new JSONObject().put("api_id", j).put("api_hash", strTrim).put("phone", strTrim2);
            editText.setText("");
            alertDialog.dismiss();
            if (z) {
                api("POST", "/api/telegram/qr/start", jSONObjectPut, new MainActivity$$ExternalSyntheticLambda88(this));
            } else {
                api("POST", "/api/telegram/start", jSONObjectPut, new MainActivity$$ExternalSyntheticLambda94(this));
            }
        } catch (Exception unused) {
            error("Проверьте api_id, api_hash и номер телефона.");
        }
    }
    public void loginNext(JSONObject jSONObject) {
        String str;
        LinearLayout linearLayout;
        EditText editText;
        MainActivity mainActivity = this;
        String strOptString = jSONObject.optString("stage");
        if (strOptString.equals("authorized")) {
            mainActivity.message("Telegram подключён. Настройте AI и выберите чаты.");
            mainActivity.fetchStatus(false);
            return;
        }
        final boolean zEquals = strOptString.equals("password");
        final boolean zEquals2 = strOptString.equals("blocked");
        if (!strOptString.equals("code") && !zEquals && !zEquals2) {
            mainActivity.message(jSONObject.optString("message", "Сначала начните вход в Telegram."));
            return;
        }
        LinearLayout linearLayoutDialogBox = mainActivity.dialogBox();
        mainActivity.add(linearLayoutDialogBox, mainActivity.label(jSONObject.optString("message"), 14, mainActivity.MUTED), 0);
        if (!jSONObject.optString("phone_last4").isEmpty()) {
            mainActivity.add(linearLayoutDialogBox, mainActivity.label("Номер заканчивается на " + jSONObject.optString("phone_last4"), 13, mainActivity.MUTED), 6);
        }
        if (strOptString.equals("code") && !jSONObject.has("delivery")) {
            mainActivity.add(linearLayoutDialogBox, mainActivity.label("Сервер пока не показывает способ доставки. Обновите его кнопкой «Установить сервер из приложения», затем повторите вход.", 13, mainActivity.MUTED), 6);
        }
        if (zEquals2) {
            linearLayout = linearLayoutDialogBox;
            editText = null;
        } else {
            if (zEquals) {
                str = "Облачный пароль Telegram";
            } else {
                str = jSONObject.optString("input").equals("text") ? "Слово или фраза из SMS" : "Код входа";
            }
            EditText editTextField = field(linearLayoutDialogBox, str, "", true, zEquals ? 256 : 128);
            mainActivity = this;
            linearLayout = linearLayoutDialogBox;
            editText = editTextField;
        }
        if (!zEquals && !zEquals2 && !jSONObject.optString("input").equals("text")) {
            editText.setInputType(18);
        }
        final AlertDialog alertDialogDialog = mainActivity.dialog(zEquals ? "Двухэтапная проверка" : "Доставка кода Telegram", linearLayout, zEquals2 ? "Закрыть" : "Продолжить");
        final Runnable[] runnableArr = {null};
        if (!zEquals && jSONObject.has("delivery")) {
            mainActivity.add(linearLayout, mainActivity.label("Сервер получил ответ Telegram о способе входа. Получение самого кода ещё не подтверждено.", 12, mainActivity.MUTED), 8);
            if (jSONObject.optBoolean("resend_available")) {
                final Button button = mainActivity.button("Повторить запрос кода", false);
                mainActivity.add(linearLayout, button, 10);
                final long jElapsedRealtime = SystemClock.elapsedRealtime() + (Math.max(0L, jSONObject.optLong("resend_after")) * 1000);
                final Runnable runnable = new Runnable() { // from class: ru.replyai.android.MainActivity.2
                    @Override // java.lang.Runnable
                    public void run() {
                        if (!alertDialogDialog.isShowing() || MainActivity.this.isDestroyed()) {
                            return;
                        }
                        long jMax = Math.max(0L, ((jElapsedRealtime - SystemClock.elapsedRealtime()) + 999) / 1000);
                        button.setEnabled(jMax == 0);
                        button.setText(jMax > 0 ? "Повторить запрос через " + jMax + " с" : "Повторить запрос кода");
                        if (jMax > 0) {
                            MainActivity.this.handler.postDelayed(this, 1000L);
                        }
                    }
                };
                alertDialogDialog.setOnDismissListener(new DialogInterface.OnDismissListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda3
                    @Override // android.content.DialogInterface.OnDismissListener
                    public final void onDismiss(DialogInterface dialogInterface) {
                        MainActivity.this.m1710lambda$loginNext$60$rureplyaiandroidMainActivity(runnable, dialogInterface);
                    }
                });
                button.setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda4
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        MainActivity.this.m1711lambda$loginNext$61$rureplyaiandroidMainActivity(alertDialogDialog, view);
                    }
                });
                runnableArr[0] = runnable;
            } else {
                mainActivity.add(linearLayout, mainActivity.label("Telegram не предоставил следующий способ доставки для этого запроса. Многократное начало входа не ускоряет доставку.", 12, mainActivity.MUTED), 8);
            }
        }
        final EditText editText2 = editText;
        alertDialogDialog.setOnShowListener(new DialogInterface.OnShowListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda5
            @Override // android.content.DialogInterface.OnShowListener
            public final void onShow(DialogInterface dialogInterface) {
                MainActivity.this.m1713lambda$loginNext$63$rureplyaiandroidMainActivity(runnableArr, alertDialogDialog, zEquals2, editText2, zEquals, dialogInterface);
            }
        });
        alertDialogDialog.show();
    }
    /* synthetic */ void m1710lambda$loginNext$60$rureplyaiandroidMainActivity(Runnable runnable, DialogInterface dialogInterface) {
        this.handler.removeCallbacks(runnable);
    }
    /* synthetic */ void m1711lambda$loginNext$61$rureplyaiandroidMainActivity(AlertDialog alertDialog, View view) {
        alertDialog.dismiss();
        api("POST", "/api/telegram/resend", new JSONObject(), new MainActivity$$ExternalSyntheticLambda94(this));
    }
    /* synthetic */ void m1713lambda$loginNext$63$rureplyaiandroidMainActivity(Runnable[] runnableArr, final AlertDialog alertDialog, final boolean z, final EditText editText, final boolean z2, DialogInterface dialogInterface) {
        Runnable runnable = runnableArr[0];
        if (runnable != null) {
            this.handler.post(runnable);
        }
        alertDialog.getButton(-1).setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda21
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1712lambda$loginNext$62$rureplyaiandroidMainActivity(z, alertDialog, editText, z2, view);
            }
        });
    }
    /* synthetic */ void m1712lambda$loginNext$62$rureplyaiandroidMainActivity(boolean z, AlertDialog alertDialog, EditText editText, boolean z2, View view) {
        if (z) {
            alertDialog.dismiss();
            return;
        }
        try {
            String string = editText.getText().toString();
            if (string.isEmpty()) {
                error("Введите значение.");
                return;
            }
            JSONObject jSONObjectPut = new JSONObject().put(z2 ? "password" : "code", string);
            editText.setText("");
            alertDialog.dismiss();
            api("POST", z2 ? "/api/telegram/password" : "/api/telegram/code", jSONObjectPut, new MainActivity$$ExternalSyntheticLambda94(this));
        } catch (Exception unused) {
            error("Не удалось создать запрос.");
        }
    }

    private void chooseChats() {
        api("GET", "/api/chats", null, new Result() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda32
            @Override // ru.replyai.android.MainActivity.Result
            public final void accept(JSONObject jSONObject) throws Exception {
                MainActivity.this.m1700lambda$chooseChats$67$rureplyaiandroidMainActivity(jSONObject);
            }
        });
    }
    /* synthetic */ void m1700lambda$chooseChats$67$rureplyaiandroidMainActivity(JSONObject jSONObject) throws Exception {
        final JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("chats");
        if (jSONArrayOptJSONArray == null || jSONArrayOptJSONArray.length() == 0) {
            message("Не найдено личных чатов с людьми. Сначала подключите Telegram и начните переписку.");
            return;
        }
        final LinearLayout linearLayoutDialogBox = dialogBox();
        add(linearLayoutDialogBox, label("Выберите личные чаты, в которых нужны автоответы. Новые сообщения и до трёх последних пар вопрос–ответ будут передаваться выбранному AI провайдеру.", 14, this.MUTED), 0);
        final CheckBox checkBox = new CheckBox(this);
        checkBox.setText("Я согласовал с выбранными собеседниками автоответы и передачу новых сообщений в AI.");
        checkBox.setTextSize(15.0f);
        checkBox.setTextColor(this.INK);
        checkBox.setTypeface(null, 1);
        add(linearLayoutDialogBox, checkBox, 12);
        add(linearLayoutDialogBox, label("Этот флажок отмечаете вы после согласования с собеседниками. Затем выберите чаты ниже и нажмите «Сохранить».", 13, this.MUTED), 4);
        final CheckBox[] checkBoxArr = new CheckBox[jSONArrayOptJSONArray.length()];
        for (int i = 0; i < jSONArrayOptJSONArray.length(); i++) {
            JSONObject jSONObjectOptJSONObject = jSONArrayOptJSONArray.optJSONObject(i);
            CheckBox checkBox2 = new CheckBox(this);
            checkBox2.setText(jSONObjectOptJSONObject.optString("name"));
            checkBox2.setChecked(jSONObjectOptJSONObject.optBoolean("selected"));
            checkBox2.setTextColor(this.INK);
            checkBoxArr[i] = checkBox2;
            add(linearLayoutDialogBox, checkBox2, 4);
        }
        add(linearLayoutDialogBox, label("Команда /ai_off от собеседника отключает автоответы в его чате. Для повторного включения нужно новое согласие.", 12, this.MUTED), 6);
        final AlertDialog alertDialogDialog = dialog("Личные чаты", linearLayoutDialogBox, "Сохранить");
        alertDialogDialog.setOnShowListener(new DialogInterface.OnShowListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda81
            @Override // android.content.DialogInterface.OnShowListener
            public final void onShow(DialogInterface dialogInterface) {
                MainActivity.this.m1699lambda$chooseChats$66$rureplyaiandroidMainActivity(alertDialogDialog, checkBoxArr, jSONArrayOptJSONArray, checkBox, linearLayoutDialogBox, dialogInterface);
            }
        });
        alertDialogDialog.show();
    }
    /* synthetic */ void m1699lambda$chooseChats$66$rureplyaiandroidMainActivity(final AlertDialog alertDialog, final CheckBox[] checkBoxArr, final JSONArray jSONArray, final CheckBox checkBox, final LinearLayout linearLayout, DialogInterface dialogInterface) {
        alertDialog.getButton(-1).setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda82
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1698lambda$chooseChats$65$rureplyaiandroidMainActivity(checkBoxArr, jSONArray, checkBox, linearLayout, alertDialog, view);
            }
        });
    }
    /* synthetic */ void m1698lambda$chooseChats$65$rureplyaiandroidMainActivity(CheckBox[] checkBoxArr, JSONArray jSONArray, CheckBox checkBox, LinearLayout linearLayout, AlertDialog alertDialog, View view) {
        try {
            JSONArray jSONArray2 = new JSONArray();
            for (int i = 0; i < checkBoxArr.length; i++) {
                if (checkBoxArr[i].isChecked()) {
                    jSONArray2.put(jSONArray.optJSONObject(i).optString("id"));
                }
            }
            if (jSONArray2.length() > 0 && !checkBox.isChecked()) {
                checkBox.requestFocus();
                if (linearLayout.getParent() instanceof ScrollView) {
                    ((ScrollView) linearLayout.getParent()).smoothScrollTo(0, 0);
                }
                error("В начале списка отметьте флажок «Я согласовал…», если согласие выбранных собеседников получено. После этого нажмите «Сохранить».");
                return;
            }
            JSONObject jSONObjectPut = new JSONObject().put("ids", jSONArray2).put("consent", checkBox.isChecked());
            alertDialog.dismiss();
            api("PUT", "/api/chats", jSONObjectPut, new Result() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda84
                @Override // ru.replyai.android.MainActivity.Result
                public final void accept(JSONObject jSONObject) throws Exception {
                    MainActivity.this.m1697lambda$chooseChats$64$rureplyaiandroidMainActivity(jSONObject);
                }
            });
        } catch (Exception unused) {
            error("Не удалось сохранить выбор.");
        }
    }
    /* synthetic */ void m1697lambda$chooseChats$64$rureplyaiandroidMainActivity(JSONObject jSONObject) throws Exception {
        message(jSONObject.optString("message"));
        fetchStatus(false);
    }
    public void formatDialog(JSONObject jSONObject) {
        if (!jSONObject.has("formatting")) {
            new AlertDialog.Builder(this).setTitle("Обновите сервер до 2.6").setMessage("Для оформления ответов нужна новая серверная часть. В разделе VPS нажмите «Установить сервер из приложения»: подключение Telegram и сохранённые ключи останутся на сервере.").setPositiveButton("Открыть раздел VPS", new DialogInterface.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda24
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.m1704lambda$formatDialog$68$rureplyaiandroidMainActivity(dialogInterface, i);
                }
            }).setNegativeButton("Позже", (DialogInterface.OnClickListener) null).show();
            return;
        }
        LinearLayout linearLayoutDialogBox = dialogBox();
        add(linearLayoutDialogBox, label("В Telegram придёт уже оформленный текст. В автоматическом режиме AI сам выделяет важные части ответа.", 14, this.MUTED), 0);
        final String[] strArr = {"auto", "plain", "bold", "underline", "mono"};
        final Spinner spinner = new Spinner(this);
        spinner.setAdapter((SpinnerAdapter) new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Автоматическое", "Обычный текст", "Жирный текст", "Подчёркнутый текст", "Моноширинный текст"}));
        int i = 0;
        for (int i2 = 0; i2 < 5; i2++) {
            if (strArr[i2].equals(jSONObject.optString("formatting"))) {
                i = i2;
            }
        }
        spinner.setSelection(i);
        add(linearLayoutDialogBox, spinner, 12);
        add(linearLayoutDialogBox, label("Предпросмотр", 13, this.MUTED), 16);
        final TextView textViewLabel = label("", 16, this.INK);
        textViewLabel.setText(formatSample(strArr[i]));
        add(linearLayoutDialogBox, textViewLabel, 8);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() { // from class: ru.replyai.android.MainActivity.3
            @Override // android.widget.AdapterView.OnItemSelectedListener
            public void onNothingSelected(AdapterView<?> adapterView) {
            }

            @Override // android.widget.AdapterView.OnItemSelectedListener
            public void onItemSelected(AdapterView<?> adapterView, View view, int i3, long j) {
                textViewLabel.setText(MainActivity.this.formatSample(strArr[i3]));
            }
        });
        add(linearLayoutDialogBox, label("Проверить настоящий ответ можно кнопкой «Проверить AI без отправки в Telegram».", 13, this.MUTED), 14);
        final AlertDialog alertDialogDialog = dialog("Оформление ответов", linearLayoutDialogBox, "Сохранить");
        alertDialogDialog.setOnShowListener(new DialogInterface.OnShowListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda25
            @Override // android.content.DialogInterface.OnShowListener
            public final void onShow(DialogInterface dialogInterface) {
                MainActivity.this.m1707lambda$formatDialog$71$rureplyaiandroidMainActivity(alertDialogDialog, strArr, spinner, dialogInterface);
            }
        });
        alertDialogDialog.show();
    }
    /* synthetic */ void m1704lambda$formatDialog$68$rureplyaiandroidMainActivity(DialogInterface dialogInterface, int i) {
        showScreen(Screen.CONNECTION);
    }
    /* synthetic */ void m1707lambda$formatDialog$71$rureplyaiandroidMainActivity(final AlertDialog alertDialog, final String[] strArr, final Spinner spinner, DialogInterface dialogInterface) {
        alertDialog.getButton(-1).setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda34
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1706lambda$formatDialog$70$rureplyaiandroidMainActivity(strArr, spinner, alertDialog, view);
            }
        });
    }
    /* synthetic */ void m1706lambda$formatDialog$70$rureplyaiandroidMainActivity(String[] strArr, Spinner spinner, AlertDialog alertDialog, View view) {
        try {
            JSONObject jSONObjectPut = new JSONObject().put("mode", strArr[spinner.getSelectedItemPosition()]);
            alertDialog.dismiss();
            api("PUT", "/api/formatting", jSONObjectPut, new Result() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda68
                @Override // ru.replyai.android.MainActivity.Result
                public final void accept(JSONObject jSONObject) throws Exception {
                    MainActivity.this.m1705lambda$formatDialog$69$rureplyaiandroidMainActivity(jSONObject);
                }
            });
        } catch (Exception unused) {
            error("Не удалось сохранить оформление.");
        }
    }
    /* synthetic */ void m1705lambda$formatDialog$69$rureplyaiandroidMainActivity(JSONObject jSONObject) throws Exception {
        message(jSONObject.optString("message"));
    }
    public CharSequence formatSample(String str) {
        SpannableString spannableString = new SpannableString("Главная мысль — коротко и ясно.\nВажный акцент не потеряется.\n/help — пример команды.");
        if (str.equals("auto")) {
            spannableString.setSpan(new StyleSpan(1), 0, 13, 33);
            int iIndexOf = "Главная мысль — коротко и ясно.\nВажный акцент не потеряется.\n/help — пример команды.".indexOf("Важный акцент");
            spannableString.setSpan(new UnderlineSpan(), iIndexOf, iIndexOf + 13, 33);
            int iIndexOf2 = "Главная мысль — коротко и ясно.\nВажный акцент не потеряется.\n/help — пример команды.".indexOf("/help");
            spannableString.setSpan(new TypefaceSpan("monospace"), iIndexOf2, iIndexOf2 + 5, 33);
            return spannableString;
        }
        applyStyle(spannableString, str, 0, "Главная мысль — коротко и ясно.\nВажный акцент не потеряется.\n/help — пример команды.".length());
        return spannableString;
    }

    private void applyStyle(SpannableString spannableString, String str, int i, int i2) {
        Object strikethroughSpan;
        if (str.equals("bold")) {
            strikethroughSpan = new StyleSpan(1);
        } else if (str.equals("italic")) {
            strikethroughSpan = new StyleSpan(2);
        } else if (str.equals("underline")) {
            strikethroughSpan = new UnderlineSpan();
        } else if (str.equals("mono")) {
            strikethroughSpan = new TypefaceSpan("monospace");
        } else {
            strikethroughSpan = str.equals("strike") ? new StrikethroughSpan() : null;
        }
        if (strikethroughSpan == null || i < 0 || i2 <= i || i2 > spannableString.length()) {
            return;
        }
        spannableString.setSpan(strikethroughSpan, i, i2, 33);
    }

    private CharSequence answerPreview(JSONObject jSONObject) {
        SpannableString spannableString = new SpannableString(jSONObject.optString("answer"));
        JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("entities");
        if (jSONArrayOptJSONArray != null) {
            for (int i = 0; i < jSONArrayOptJSONArray.length(); i++) {
                JSONObject jSONObjectOptJSONObject = jSONArrayOptJSONArray.optJSONObject(i);
                if (jSONObjectOptJSONObject != null) {
                    int iOptInt = jSONObjectOptJSONObject.optInt("offset", -1);
                    int iOptInt2 = jSONObjectOptJSONObject.optInt("length", -1);
                    if (iOptInt2 > 0 && iOptInt >= 0 && iOptInt2 <= spannableString.length() - iOptInt) {
                        applyStyle(spannableString, jSONObjectOptJSONObject.optString("type"), iOptInt, iOptInt2 + iOptInt);
                    }
                }
            }
        }
        return spannableString;
    }
    public void aiDialog(JSONObject jSONObject) {
        int i;
        LinearLayout linearLayoutDialogBox = dialogBox();
        add(linearLayoutDialogBox, label("Ключ хранится на сервере в зашифрованном виде. Оставьте поле пустым, чтобы сохранить прежний ключ того же провайдера. Доступность и квоты API проверяются кнопкой теста.", 14, this.MUTED), 0);
        final Spinner spinner = new Spinner(this);
        final String[] strArr = {"groq", "gemini", "custom", "gigachat"};
        spinner.setAdapter((SpinnerAdapter) new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Groq", "Google Gemini", "Свой OpenAI-совместимый API", "GigaChat — физлицо"}));
        if (jSONObject.optString("provider").equals("gigachat")) {
            i = 3;
        } else if (jSONObject.optString("provider").equals("gemini")) {
            i = 1;
        } else {
            i = jSONObject.optString("provider").equals("custom") ? 2 : 0;
        }
        spinner.setSelection(i);
        add(linearLayoutDialogBox, spinner, 10);
        final EditText editTextField = field(linearLayoutDialogBox, "API-ключ", "", true, 2048);
        editTextField.setHint(jSONObject.optBoolean("has_key") ? "Сохранён на сервере" : "API-ключ / Authorization Key GigaChat");
        final EditText editTextField2 = field(linearLayoutDialogBox, "Модель", jSONObject.optString("model"), false, 160);
        final EditText editTextField3 = field(linearLayoutDialogBox, "Полный HTTPS endpoint для своего API", jSONObject.optString("endpoint"), false, 2048);
        editTextField3.setHint("https://example.org/v1/chat/completions");
        final EditText editTextField4 = field(linearLayoutDialogBox, "Инструкция для AI", jSONObject.optString("prompt"), false, 4000);
        editTextField4.setSingleLine(false);
        editTextField4.setMinLines(3);
        final EditText editTextField5 = field(linearLayoutDialogBox, "Пауза между ответами в одном чате, секунд", String.valueOf(jSONObject.optInt("cooldown", 15)), false, 3);
        editTextField5.setInputType(2);
        final int[] iArr = {i};
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() { // from class: ru.replyai.android.MainActivity.4
            @Override // android.widget.AdapterView.OnItemSelectedListener
            public void onNothingSelected(AdapterView<?> adapterView) {
            }

            @Override // android.widget.AdapterView.OnItemSelectedListener
            public void onItemSelected(AdapterView<?> adapterView, View view, int i2, long j) {
                editTextField3.setVisibility(i2 == 2 ? 0 : 8);
                if (i2 != iArr[0]) {
                    String str = "";
                    editTextField.setText("");
                    editTextField.setHint("Введите ключ выбранного провайдера");
                    EditText editText = editTextField2;
                    if (i2 == 0) {
                        str = "openai/gpt-oss-20b";
                    } else if (i2 == 1) {
                        str = "gemini-3.5-flash-lite";
                    } else if (i2 == 3) {
                        str = "GigaChat";
                    }
                    editText.setText(str);
                    iArr[0] = i2;
                }
            }
        });
        final AlertDialog alertDialogDialog = dialog("AI API", linearLayoutDialogBox, "Сохранить на сервере");
        alertDialogDialog.setOnShowListener(new DialogInterface.OnShowListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda28
            @Override // android.content.DialogInterface.OnShowListener
            public final void onShow(DialogInterface dialogInterface) {
                MainActivity.this.m1652lambda$aiDialog$74$rureplyaiandroidMainActivity(alertDialogDialog, editTextField5, strArr, spinner, editTextField, editTextField2, editTextField3, editTextField4, dialogInterface);
            }
        });
        alertDialogDialog.show();
    }
    /* synthetic */ void m1652lambda$aiDialog$74$rureplyaiandroidMainActivity(final AlertDialog alertDialog, final EditText editText, final String[] strArr, final Spinner spinner, final EditText editText2, final EditText editText3, final EditText editText4, final EditText editText5, DialogInterface dialogInterface) {
        alertDialog.getButton(-1).setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda43
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1651lambda$aiDialog$73$rureplyaiandroidMainActivity(editText, strArr, spinner, editText2, editText3, editText4, editText5, alertDialog, view);
            }
        });
    }
    /* synthetic */ void m1651lambda$aiDialog$73$rureplyaiandroidMainActivity(EditText editText, String[] strArr, Spinner spinner, EditText editText2, EditText editText3, EditText editText4, EditText editText5, AlertDialog alertDialog, View view) {
        try {
            int i = Integer.parseInt(editText.getText().toString());
            if (i < 0 || i > 300) {
                throw new Exception();
            }
            JSONObject jSONObjectPut = new JSONObject().put("provider", strArr[spinner.getSelectedItemPosition()]).put("key", editText2.getText().toString().trim()).put("model", editText3.getText().toString().trim()).put("endpoint", editText4.getText().toString().trim()).put("prompt", editText5.getText().toString()).put("cooldown", i);
            editText2.setText("");
            alertDialog.dismiss();
            api("PUT", "/api/settings", jSONObjectPut, new Result() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda90
                @Override // ru.replyai.android.MainActivity.Result
                public final void accept(JSONObject jSONObject) throws Exception {
                    MainActivity.this.m1650lambda$aiDialog$72$rureplyaiandroidMainActivity(jSONObject);
                }
            });
        } catch (Exception unused) {
            error("Проверьте поля. Пауза — число от 0 до 300 секунд.");
        }
    }
    /* synthetic */ void m1650lambda$aiDialog$72$rureplyaiandroidMainActivity(JSONObject jSONObject) throws Exception {
        message(jSONObject.optString("message"));
    }

    private void testDialog() {
        LinearLayout linearLayoutDialogBox = dialogBox();
        add(linearLayoutDialogBox, label("Запрос использует сохранённые настройки сервера и квоту AI API. Ответ появится только здесь.", 14, this.MUTED), 0);
        final EditText editTextField = field(linearLayoutDialogBox, "Тестовый вопрос", "Привет! Представься одним предложением.", false, 4000);
        editTextField.setSingleLine(false);
        final AlertDialog alertDialogDialog = dialog("Проверка AI", linearLayoutDialogBox, "Отправить тест");
        alertDialogDialog.setOnShowListener(new DialogInterface.OnShowListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda93
            @Override // android.content.DialogInterface.OnShowListener
            public final void onShow(DialogInterface dialogInterface) {
                MainActivity.this.m1726lambda$testDialog$77$rureplyaiandroidMainActivity(alertDialogDialog, editTextField, dialogInterface);
            }
        });
        alertDialogDialog.show();
    }
    /* synthetic */ void m1726lambda$testDialog$77$rureplyaiandroidMainActivity(final AlertDialog alertDialog, final EditText editText, DialogInterface dialogInterface) {
        alertDialog.getButton(-1).setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda40
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m1725lambda$testDialog$76$rureplyaiandroidMainActivity(editText, alertDialog, view);
            }
        });
    }
    /* synthetic */ void m1725lambda$testDialog$76$rureplyaiandroidMainActivity(EditText editText, AlertDialog alertDialog, View view) {
        try {
            JSONObject jSONObjectPut = new JSONObject().put("text", editText.getText().toString());
            alertDialog.dismiss();
            api("POST", "/api/test", jSONObjectPut, new Result() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda70
                @Override // ru.replyai.android.MainActivity.Result
                public final void accept(JSONObject jSONObject) throws Exception {
                    MainActivity.this.m1724lambda$testDialog$75$rureplyaiandroidMainActivity(jSONObject);
                }
            });
        } catch (Exception unused) {
            error("Не удалось создать запрос.");
        }
    }
    /* synthetic */ void m1724lambda$testDialog$75$rureplyaiandroidMainActivity(JSONObject jSONObject) throws Exception {
        message(answerPreview(jSONObject));
    }

    private void api(final String str, final String str2, final JSONObject jSONObject, final Result result) {
        try {
            final RemoteClient remoteClient = new RemoteClient(this.settings.url(), this.settings.token());
            final long j = this.connectionGeneration;
            final long j2 = this.screenGeneration;
            this.network.execute(new Runnable() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda62
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m1655lambda$api$80$rureplyaiandroidMainActivity(remoteClient, str, str2, jSONObject, j, j2, result);
                }
            });
        } catch (Exception unused) {
            error("Сначала сохраните HTTPS адрес и ключ управления сервером.");
        }
    }
    /* synthetic */ void m1655lambda$api$80$rureplyaiandroidMainActivity(RemoteClient remoteClient, String str, String str2, JSONObject jSONObject, final long j, final long j2, final Result result) {
        try {
            final JSONObject answer = remoteClient.call(str, str2, jSONObject);
            this.handler.post(() -> m1653lambda$api$78$rureplyaiandroidMainActivity(j, j2, result, answer));
        } catch (Exception e) {
            this.handler.post(() -> m1654lambda$api$79$rureplyaiandroidMainActivity(j, j2, e));
        }
    }
    /* synthetic */ void m1653lambda$api$78$rureplyaiandroidMainActivity(long j, long j2, Result result, JSONObject jSONObject) {
        if (isFinishing() || isDestroyed() || j != this.connectionGeneration || j2 != this.screenGeneration) {
            return;
        }
        try {
            result.accept(jSONObject);
        } catch (Exception unused) {
            error("Не удалось прочитать ответ сервера.");
        }
    }
    /* synthetic */ void m1654lambda$api$79$rureplyaiandroidMainActivity(long j, long j2, Exception exc) {
        if (isFinishing() || isDestroyed() || j != this.connectionGeneration || j2 != this.screenGeneration) {
            return;
        }
        error(exc.getMessage());
    }
    public void fetchStatus(final boolean z) {
        if (this.polling) {
            return;
        }
        try {
            final RemoteClient remoteClient = new RemoteClient(this.settings.url(), this.settings.token());
            this.polling = true;
            final long j = this.connectionGeneration;
            this.network.execute(new Runnable() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m1703lambda$fetchStatus$83$rureplyaiandroidMainActivity(remoteClient, j, z);
                }
            });
        } catch (Exception unused) {
            if (z) {
                error("Сначала сохраните подключение к серверу.");
            }
        }
    }
    /* synthetic */ void m1703lambda$fetchStatus$83$rureplyaiandroidMainActivity(RemoteClient remoteClient, final long j, final boolean z) {
        try {
            final JSONObject jSONObjectCall = remoteClient.call("GET", "/api/status", null);
            this.handler.post(new Runnable() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda78
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m1701lambda$fetchStatus$81$rureplyaiandroidMainActivity(j, jSONObjectCall);
                }
            });
        } catch (Exception e) {
            this.handler.post(new Runnable() { // from class: ru.replyai.android.MainActivity$$ExternalSyntheticLambda79
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m1702lambda$fetchStatus$82$rureplyaiandroidMainActivity(j, z, e);
                }
            });
        }
    }
    /* synthetic */ void m1701lambda$fetchStatus$81$rureplyaiandroidMainActivity(long j, JSONObject jSONObject) {
        if (j != this.connectionGeneration) {
            return;
        }
        this.polling = false;
        if (isFinishing() || isDestroyed()) {
            return;
        }
        showStatus(jSONObject);
    }
    /* synthetic */ void m1702lambda$fetchStatus$82$rureplyaiandroidMainActivity(long j, boolean z, Exception exc) {
        if (j != this.connectionGeneration) {
            return;
        }
        this.polling = false;
        if (isFinishing() || isDestroyed()) {
            return;
        }
        this.connectionUnavailable = true;
        renderStatus();
        if (z) {
            error(exc.getMessage());
        }
    }
    public void showStatus(JSONObject jSONObject) {
        this.lastStatus = jSONObject;
        this.lastStatusAt = System.currentTimeMillis();
        this.connectionUnavailable = false;
        renderStatus();
    }

    private void renderStatus() {
        String str;
        if (this.connectionUnavailable) {
            TextView textView = this.statusText;
            if (textView != null) {
                textView.setText("Нет связи с сервером");
            }
            TextView textView2 = this.detailText;
            if (textView2 != null) {
                textView2.setText("Автоответы могут продолжаться. Проверьте интернет и доступность VPS.");
            }
            TextView textView3 = this.logsText;
            if (textView3 != null && this.lastStatus == null) {
                textView3.setText("Сервер недоступен. Обновите журнал после восстановления связи.");
            }
            TextView textView4 = this.journalState;
            if (textView4 != null) {
                textView4.setText(this.lastStatus == null ? "Нет связи с VPS." : "Нет связи с VPS. Ниже — ранее полученные события.");
                return;
            }
            return;
        }
        JSONObject jSONObject = this.lastStatus;
        if (jSONObject == null) {
            return;
        }
        boolean zOptBoolean = jSONObject.optBoolean("enabled");
        boolean zOptBoolean2 = jSONObject.optBoolean("connected");
        boolean zOptBoolean3 = jSONObject.optBoolean("authorized");
        TextView textView5 = this.statusText;
        if (textView5 != null) {
            if (this.screen == Screen.TELEGRAM) {
                str = zOptBoolean3 ? "Аккаунт подключён" : "Войдите в Telegram";
            } else if (!zOptBoolean3) {
                str = "Telegram не подключён";
            } else if (!zOptBoolean2) {
                str = "Telegram переподключается";
            } else if (zOptBoolean) {
                str = jSONObject.optBoolean("quiet_active") ? "Тихие часы · автоответы на паузе" : "Автоответы включены";
            } else {
                str = "Автоответы остановлены";
            }
            textView5.setText(str);
            this.statusText.setTextColor((zOptBoolean3 && zOptBoolean2 && zOptBoolean) ? this.TEAL : this.INK);
        }
        String str2 = "Сервер: " + jSONObject.optString("version", "?") + "\n" + jSONObject.optString("account") + "\nОтправлено: " + jSONObject.optInt("sent") + " · Чатов: " + jSONObject.optInt("selected") + " · В очереди: " + jSONObject.optInt("queued");
        if (this.screen == Screen.HOME) {
            str2 = "Отправлено: " + jSONObject.optInt("sent") + "  ·  Чатов: " + jSONObject.optInt("selected") + "  ·  В очереди: " + jSONObject.optInt("queued");
        }
        if (jSONObject.optBoolean("quiet_active")) {
            str2 = str2 + "\nТихие часы включены: новые сообщения сейчас пропускаются.";
        }
        if (this.screen == Screen.TELEGRAM) {
            str2 = jSONObject.optString("account") + "\nСервер: " + jSONObject.optString("version", "?");
        }
        long jOptLong = jSONObject.optLong("pause_until") * 1000;
        if (jOptLong > System.currentTimeMillis()) {
            str2 = str2 + "\nПауза API до " + new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date(jOptLong));
        }
        if (jSONObject.optInt("uncertain") > 0) {
            str2 = str2 + "\nНеопределённых отправок: " + jSONObject.optInt("uncertain") + ". Проверьте чаты перед очисткой отметок.";
        }
        TextView textView6 = this.detailText;
        if (textView6 != null) {
            textView6.setText(str2.trim());
        }
        if (this.logsText != null) {
            TextView textView7 = this.journalState;
            if (textView7 != null) {
                textView7.setText("Обновлено в " + new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date(this.lastStatusAt)));
            }
            JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("logs");
            StringBuilder sb = new StringBuilder();
            if (jSONArrayOptJSONArray != null) {
                for (int i = 0; i < Math.min(jSONArrayOptJSONArray.length(), 60); i++) {
                    if (i > 0) {
                        sb.append("\n\n");
                    }
                    sb.append(jSONArrayOptJSONArray.optString(i));
                }
            }
            this.logsText.setText(sb.length() > 0 ? sb.toString() : "Событий пока нет. Тексты переписки не записываются в журнал.");
        }
    }

    @Override // android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if (i == 42 && i2 == -1) {
            this.connectionGeneration++;
            this.polling = false;
            this.observedSettingsUrl = this.settings.url();
            EditText editText = this.urlField;
            if (editText != null) {
                editText.setText(this.settings.url());
            }
            EditText editText2 = this.tokenField;
            if (editText2 != null) {
                editText2.setText("");
                this.tokenField.setHint("Сохранён автоматически");
            }
            fetchStatus(true);
        }
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        if (!this.settings.url().equals(this.observedSettingsUrl)) {
            String strUrl = this.settings.url();
            this.observedSettingsUrl = strUrl;
            this.connectionGeneration++;
            this.polling = false;
            this.lastStatus = null;
            this.connectionUnavailable = false;
            EditText editText = this.urlField;
            if (editText != null) {
                editText.setText(strUrl);
            }
            EditText editText2 = this.tokenField;
            if (editText2 != null) {
                editText2.setText("");
                this.tokenField.setHint("Сохранён автоматически");
            }
        }
        this.foreground = true;
        this.handler.post(this.refresh);
    }

    @Override // android.app.Activity
    protected void onPause() {
        this.foreground = false;
        this.handler.removeCallbacks(this.refresh);
        super.onPause();
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        this.network.shutdownNow();
        this.handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }

    private LinearLayout column() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        return linearLayout;
    }

    private LinearLayout dialogBox() {
        LinearLayout linearLayoutColumn = column();
        linearLayoutColumn.setPadding(dp(22), dp(8), dp(22), dp(12));
        return linearLayoutColumn;
    }

    private TextView label(String str, int i, int i2) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextSize(i);
        textView.setTextColor(i2);
        textView.setLineSpacing(dp(2), 1.0f);
        return textView;
    }

    private void add(LinearLayout linearLayout, View view, int i) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = dp(i);
        linearLayout.addView(view, layoutParams);
    }

    private LinearLayout card(String str) {
        LinearLayout linearLayoutColumn = column();
        linearLayoutColumn.setPadding(dp(18), dp(16), dp(18), dp(18));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(this.colors.card);
        gradientDrawable.setCornerRadius(dp(18));
        linearLayoutColumn.setBackground(gradientDrawable);
        TextView textViewLabel = label(str, 17, this.INK);
        textViewLabel.setTypeface(null, 1);
        add(linearLayoutColumn, textViewLabel, 0);
        add(this.content, linearLayoutColumn, 18);
        return linearLayoutColumn;
    }

    private Button button(String str, boolean z) {
        int i;
        Button button = new Button(this);
        button.setText(str);
        button.setAllCaps(false);
        button.setTextSize(14.0f);
        button.setMinHeight(dp(48));
        if (z) {
            i = this.colors.dark ? this.colors.background : -1;
        } else {
            i = this.TEAL;
        }
        button.setTextColor(i);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(z ? this.TEAL : this.colors.soft);
        gradientDrawable.setCornerRadius(dp(12));
        button.setBackground(gradientDrawable);
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        return button;
    }

    private void action(LinearLayout linearLayout, String str, View.OnClickListener onClickListener) {
        Button button = button(str, false);
        button.setOnClickListener(onClickListener);
        add(linearLayout, button, 10);
    }

    private EditText field(LinearLayout linearLayout, String str, String str2, boolean z, int i) {
        add(linearLayout, label(str, 13, this.MUTED), 12);
        EditText editText = new EditText(this);
        editText.setTextColor(this.INK);
        editText.setTextSize(15.0f);
        editText.setSingleLine(true);
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(i)});
        editText.setInputType((z ? 128 : 16384) | 1);
        editText.setImportantForAutofill(2);
        editText.setSaveEnabled(false);
        editText.setText(str2);
        add(linearLayout, editText, 2);
        return editText;
    }

    private AlertDialog dialog(String str, LinearLayout linearLayout, String str2) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(linearLayout);
        return new AlertDialog.Builder(this).setTitle(str).setView(scrollView).setNegativeButton("Отмена", (DialogInterface.OnClickListener) null).setPositiveButton(str2, (DialogInterface.OnClickListener) null).create();
    }

    private void error(String str) {
        AlertDialog.Builder title = new AlertDialog.Builder(this).setTitle("Не получилось");
        if (str == null) {
            str = "Проверьте соединение с сервером.";
        }
        title.setMessage(str).setPositiveButton("Понятно", (DialogInterface.OnClickListener) null).show();
    }

    private void message(CharSequence charSequence) {
        new AlertDialog.Builder(this).setTitle("Reply AI").setMessage(charSequence).setPositiveButton("Понятно", (DialogInterface.OnClickListener) null).show();
    }
}
