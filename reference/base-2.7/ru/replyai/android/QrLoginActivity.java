package ru.replyai.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.text.InputFilter;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public final class QrLoginActivity extends Activity {
    private boolean busy;
    private Button cancel;
    private Button check;
    private Button copy;
    private boolean foreground;
    private EditText password;
    private QrCodeView qr;
    private Button restart;
    private RemoteSettings settings;
    private TextView status;
    private Button submit;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private String viewerPath = "";
    private String viewerOrigin = "";
    private final Runnable poll = new Runnable() { // from class: ru.replyai.android.QrLoginActivity.1
        @Override // java.lang.Runnable
        public void run() {
            if (QrLoginActivity.this.foreground) {
                if (!QrLoginActivity.this.busy) {
                    QrLoginActivity.this.call("GET", "/api/telegram/qr", null, false);
                }
                QrLoginActivity.this.ui.postDelayed(this, 2000L);
            }
        }
    };

    @Override // android.app.Activity
    public void onCreate(Bundle bundle) {
        ThemeManager.apply(this);
        super.onCreate(bundle);
        this.settings = new RemoteSettings(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(ThemeManager.colors(this).background);
        ViewCompat.setOnApplyWindowInsetsListener(scrollView, new OnApplyWindowInsetsListener() { // from class: ru.replyai.android.QrLoginActivity$$ExternalSyntheticLambda4
            @Override // androidx.core.view.OnApplyWindowInsetsListener
            public final WindowInsetsCompat onApplyWindowInsets(View view, WindowInsetsCompat windowInsetsCompat) {
                return QrLoginActivity.lambda$onCreate$0(view, windowInsetsCompat);
            }
        });
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(22), dp(22), dp(22), dp(28));
        scrollView.addView(linearLayout);
        setContentView(scrollView);
        text(linearLayout, "Вход через QR", 26);
        text(linearLayout, "Скопируйте ссылку и откройте её на своём компьютере или втором телефоне. Затем на Tecno откройте Telegram → Настройки → Устройства → Подключить устройство и отсканируйте QR со второго экрана.", 16);
        text(linearLayout, "QR обновляется автоматически на VPS. Этот телефон можно переключить в Telegram. Ссылка действует до 5 минут и открывает только страницу этого входа. Открывайте её только на своём устройстве.", 14);
        TextView textViewText = text(linearLayout, "Проверяем QR-вход…", 15);
        this.status = textViewText;
        textViewText.setTextIsSelectable(true);
        this.qr = new QrCodeView(this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, dp(280));
        layoutParams.topMargin = dp(12);
        linearLayout.addView(this.qr, layoutParams);
        this.qr.setVisibility(8);
        Button button = button(linearLayout, "Копировать ссылку для второго устройства");
        this.copy = button;
        button.setEnabled(false);
        this.copy.setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.QrLoginActivity$$ExternalSyntheticLambda5
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.m1739lambda$onCreate$1$rureplyaiandroidQrLoginActivity(view);
            }
        });
        EditText editText = new EditText(this);
        this.password = editText;
        editText.setSingleLine(true);
        this.password.setHint("Облачный пароль Telegram");
        this.password.setInputType(129);
        this.password.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256)});
        this.password.setSaveEnabled(false);
        this.password.setImportantForAutofill(2);
        linearLayout.addView(this.password);
        this.password.setVisibility(8);
        Button button2 = button(linearLayout, "Подтвердить облачный пароль");
        this.submit = button2;
        button2.setVisibility(8);
        this.submit.setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.QrLoginActivity$$ExternalSyntheticLambda6
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.m1740lambda$onCreate$2$rureplyaiandroidQrLoginActivity(view);
            }
        });
        Button button3 = button(linearLayout, "Проверить вход");
        this.check = button3;
        button3.setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.QrLoginActivity$$ExternalSyntheticLambda7
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.m1741lambda$onCreate$3$rureplyaiandroidQrLoginActivity(view);
            }
        });
        Button button4 = button(linearLayout, "Создать новую ссылку QR");
        this.restart = button4;
        button4.setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.QrLoginActivity$$ExternalSyntheticLambda8
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.m1742lambda$onCreate$4$rureplyaiandroidQrLoginActivity(view);
            }
        });
        Button button5 = button(linearLayout, "Отменить QR-вход");
        this.cancel = button5;
        button5.setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.QrLoginActivity$$ExternalSyntheticLambda9
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.m1743lambda$onCreate$5$rureplyaiandroidQrLoginActivity(view);
            }
        });
        button(linearLayout, "Сохранить диагностику").setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.QrLoginActivity$$ExternalSyntheticLambda10
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.m1744lambda$onCreate$6$rureplyaiandroidQrLoginActivity(view);
            }
        });
        button(linearLayout, "Вернуться к управлению").setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.QrLoginActivity$$ExternalSyntheticLambda11
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.m1745lambda$onCreate$7$rureplyaiandroidQrLoginActivity(view);
            }
        });
        text(linearLayout, "При закрытии экрана вход остаётся доступен до истечения срока. Кнопка «Отменить QR-вход» немедленно закрывает ссылку. Пароль двухэтапной проверки вводится только здесь, если Telegram его запросит.", 13);
    }

    static /* synthetic */ WindowInsetsCompat lambda$onCreate$0(View view, WindowInsetsCompat windowInsetsCompat) {
        Insets insets = windowInsetsCompat.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
        view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
        return windowInsetsCompat;
    }

    /* JADX INFO: renamed from: lambda$onCreate$1$ru-replyai-android-QrLoginActivity, reason: not valid java name */
    /* synthetic */ void m1739lambda$onCreate$1$rureplyaiandroidQrLoginActivity(View view) {
        copyLink();
    }

    /* JADX INFO: renamed from: lambda$onCreate$2$ru-replyai-android-QrLoginActivity, reason: not valid java name */
    /* synthetic */ void m1740lambda$onCreate$2$rureplyaiandroidQrLoginActivity(View view) {
        try {
            String string = this.password.getText().toString();
            if (string.isEmpty()) {
                this.status.setText("Введите облачный пароль Telegram.");
                return;
            }
            JSONObject jSONObjectPut = new JSONObject().put("password", string);
            this.password.setText("");
            call("POST", "/api/telegram/password", jSONObjectPut, true);
        } catch (Exception e) {
            DiagnosticLog.event("telegram", "qr_failed", e);
        }
    }

    /* JADX INFO: renamed from: lambda$onCreate$3$ru-replyai-android-QrLoginActivity, reason: not valid java name */
    /* synthetic */ void m1741lambda$onCreate$3$rureplyaiandroidQrLoginActivity(View view) {
        call("GET", "/api/telegram/qr", null, true);
    }

    /* JADX INFO: renamed from: lambda$onCreate$4$ru-replyai-android-QrLoginActivity, reason: not valid java name */
    /* synthetic */ void m1742lambda$onCreate$4$rureplyaiandroidQrLoginActivity(View view) {
        restart();
    }

    /* JADX INFO: renamed from: lambda$onCreate$5$ru-replyai-android-QrLoginActivity, reason: not valid java name */
    /* synthetic */ void m1743lambda$onCreate$5$rureplyaiandroidQrLoginActivity(View view) {
        call("POST", "/api/telegram/qr/cancel", new JSONObject(), true);
    }

    /* JADX INFO: renamed from: lambda$onCreate$6$ru-replyai-android-QrLoginActivity, reason: not valid java name */
    /* synthetic */ void m1744lambda$onCreate$6$rureplyaiandroidQrLoginActivity(View view) {
        startActivity(new Intent(this, (Class<?>) DiagnosticsActivity.class));
    }

    /* JADX INFO: renamed from: lambda$onCreate$7$ru-replyai-android-QrLoginActivity, reason: not valid java name */
    /* synthetic */ void m1745lambda$onCreate$7$rureplyaiandroidQrLoginActivity(View view) {
        finish();
    }

    private void copyLink() {
        try {
            String strViewerLink = viewerLink(this.viewerOrigin, this.viewerPath);
            if (!this.viewerOrigin.equals(this.settings.url())) {
                throw new IllegalStateException();
            }
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService("clipboard");
            ClipData clipDataNewPlainText = ClipData.newPlainText("Вход в свой Reply AI", strViewerLink);
            PersistableBundle persistableBundle = new PersistableBundle();
            persistableBundle.putBoolean("android.content.extra.IS_SENSITIVE", true);
            clipDataNewPlainText.getDescription().setExtras(persistableBundle);
            clipboardManager.setPrimaryClip(clipDataNewPlainText);
            this.status.setText("Ссылка скопирована. Откройте её на своём втором устройстве. QR на странице обновляется автоматически; затем подтвердите вход в Telegram на Tecno.");
        } catch (Exception e) {
            DiagnosticLog.event("telegram", "qr_failed", e);
            this.status.setText("Не удалось скопировать ссылку. Проверьте подключение и нажмите «Проверить вход».");
        }
    }

    static String viewerLink(String str, String str2) throws Exception {
        if (!str2.matches("/qr#[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException();
        }
        return RemoteClient.normalizeUrl(str) + str2;
    }

    private void restart() {
        if (this.busy) {
            return;
        }
        this.busy = true;
        buttons(false);
        this.status.setText("Создаём новую ссылку…");
        final String strUrl = this.settings.url();
        final String str = this.settings.token();
        this.worker.execute(new Runnable() { // from class: ru.replyai.android.QrLoginActivity$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.m1747lambda$restart$9$rureplyaiandroidQrLoginActivity(strUrl, str);
            }
        });
    }

    /* JADX INFO: renamed from: lambda$restart$9$ru-replyai-android-QrLoginActivity, reason: not valid java name */
    /* synthetic */ void m1747lambda$restart$9$rureplyaiandroidQrLoginActivity(final String str, String str2) {
        try {
            RemoteClient remoteClient = new RemoteClient(str, str2);
            remoteClient.call("POST", "/api/telegram/qr/cancel", new JSONObject());
            final JSONObject jSONObjectCall = remoteClient.call("POST", "/api/telegram/qr/start", null);
            this.ui.post(new Runnable() { // from class: ru.replyai.android.QrLoginActivity$$ExternalSyntheticLambda3
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.m1746lambda$restart$8$rureplyaiandroidQrLoginActivity(jSONObjectCall, str);
                }
            });
        } catch (Exception e) {
            fail(e, true);
        }
    }

    /* JADX INFO: renamed from: lambda$restart$8$ru-replyai-android-QrLoginActivity, reason: not valid java name */
    /* synthetic */ void m1746lambda$restart$8$rureplyaiandroidQrLoginActivity(JSONObject jSONObject, String str) {
        if (alive()) {
            this.busy = false;
            buttons(true);
            render(jSONObject, str);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void call(final String str, final String str2, final JSONObject jSONObject, final boolean z) {
        if (this.busy) {
            return;
        }
        this.busy = true;
        buttons(false);
        final String strUrl = this.settings.url();
        final String str3 = this.settings.token();
        this.worker.execute(new Runnable() { // from class: ru.replyai.android.QrLoginActivity$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.m1737lambda$call$11$rureplyaiandroidQrLoginActivity(strUrl, str3, str, str2, jSONObject, z);
            }
        });
    }

    /* JADX INFO: renamed from: lambda$call$11$ru-replyai-android-QrLoginActivity, reason: not valid java name */
    /* synthetic */ void m1737lambda$call$11$rureplyaiandroidQrLoginActivity(final String str, String str2, String str3, String str4, JSONObject jSONObject, boolean z) {
        try {
            RemoteClient remoteClient = new RemoteClient(str, str2);
            final JSONObject jSONObjectCall = remoteClient.call(str3, str4, jSONObject);
            if (str4.equals("/api/telegram/password")) {
                jSONObjectCall = remoteClient.call("GET", "/api/telegram/qr", null);
            }
            this.ui.post(new Runnable() { // from class: ru.replyai.android.QrLoginActivity$$ExternalSyntheticLambda12
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.m1736lambda$call$10$rureplyaiandroidQrLoginActivity(jSONObjectCall, str);
                }
            });
        } catch (Exception e) {
            fail(e, z);
        }
    }

    /* JADX INFO: renamed from: lambda$call$10$ru-replyai-android-QrLoginActivity, reason: not valid java name */
    /* synthetic */ void m1736lambda$call$10$rureplyaiandroidQrLoginActivity(JSONObject jSONObject, String str) {
        if (alive()) {
            this.busy = false;
            buttons(true);
            render(jSONObject, str);
        }
    }

    private void fail(final Exception exc, boolean z) {
        if (z) {
            DiagnosticLog.event("telegram", "qr_failed", exc);
        }
        this.ui.post(new Runnable() { // from class: ru.replyai.android.QrLoginActivity$$ExternalSyntheticLambda2
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.m1738lambda$fail$12$rureplyaiandroidQrLoginActivity(exc);
            }
        });
    }

    /* JADX INFO: renamed from: lambda$fail$12$ru-replyai-android-QrLoginActivity, reason: not valid java name */
    /* synthetic */ void m1738lambda$fail$12$rureplyaiandroidQrLoginActivity(Exception exc) {
        if (alive()) {
            this.busy = false;
            buttons(true);
            this.status.setText(exc.getMessage() == null ? "Нет связи с сервером. Сохраните диагностику." : exc.getMessage());
        }
    }

    void render(JSONObject jSONObject, String str) {
        String strOptString = jSONObject.optString("stage");
        boolean zEquals = strOptString.equals("qr");
        boolean zEquals2 = strOptString.equals("password");
        this.status.setText(jSONObject.optString("message", "Проверьте состояние входа."));
        this.password.setVisibility(zEquals2 ? 0 : 8);
        this.submit.setVisibility(zEquals2 ? 0 : 8);
        if (!zEquals2) {
            this.password.setText("");
        }
        String strOptString2 = jSONObject.optString("viewer_path");
        this.viewerPath = strOptString2;
        this.viewerOrigin = str;
        this.copy.setEnabled(zEquals && strOptString2.matches("/qr#[A-Za-z0-9_-]{43}"));
        try {
            this.qr.setRows(zEquals ? jSONObject.optJSONArray("qr_rows") : null);
            this.qr.setVisibility((zEquals && jSONObject.has("qr_rows")) ? 0 : 8);
        } catch (Exception e) {
            this.qr.setRows(null);
            this.qr.setVisibility(8);
            this.status.setText("Некорректный QR от сервера. Сохраните диагностику.");
            DiagnosticLog.event("telegram", "qr_failed", e);
        }
        this.restart.setEnabled((strOptString.equals("authorized") || zEquals2) ? false : true);
        this.cancel.setEnabled(!strOptString.equals("authorized"));
        if (strOptString.equals("authorized")) {
            this.viewerPath = "";
            this.copy.setEnabled(false);
            setResult(-1);
        }
    }

    private void buttons(boolean z) {
        this.check.setEnabled(z);
        this.restart.setEnabled(z);
        this.submit.setEnabled(z);
        this.cancel.setEnabled(z);
        this.copy.setEnabled(z && !this.viewerPath.isEmpty());
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        this.foreground = true;
        this.ui.post(this.poll);
    }

    @Override // android.app.Activity
    protected void onPause() {
        this.foreground = false;
        this.ui.removeCallbacks(this.poll);
        super.onPause();
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        this.worker.shutdownNow();
        this.ui.removeCallbacksAndMessages(null);
        this.viewerPath = "";
        this.password.setText("");
        this.qr.setRows(null);
        super.onDestroy();
    }

    private boolean alive() {
        return (isFinishing() || isDestroyed()) ? false : true;
    }

    private int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }

    private TextView text(LinearLayout linearLayout, String str, int i) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextSize(i);
        textView.setTextColor(ThemeManager.colors(this).ink);
        textView.setPadding(0, dp(8), 0, dp(10));
        linearLayout.addView(textView);
        return textView;
    }

    private Button button(LinearLayout linearLayout, String str) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(str);
        linearLayout.addView(button);
        return button;
    }
}
