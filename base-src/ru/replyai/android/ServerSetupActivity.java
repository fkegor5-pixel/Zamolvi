package ru.replyai.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public final class ServerSetupActivity extends Activity {
    private volatile SshInstaller active;
    private boolean busy;
    private EditText domain;
    private Button done;
    private EditText ip;
    private EditText password;
    private EditText port;
    private SharedPreferences prefs;
    private ProgressBar progress;
    private volatile byte[] retryPassword;
    private Button start;
    private TextView status;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override // android.app.Activity
    public void onCreate(Bundle bundle) {
        ThemeManager.apply(this);
        super.onCreate(bundle);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        this.prefs = getSharedPreferences("reply_ai_setup", 0);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(ThemeManager.colors(this).background);
        ViewCompat.setOnApplyWindowInsetsListener(scrollView, new OnApplyWindowInsetsListener() { // from class: ru.replyai.android.ServerSetupActivity$$ExternalSyntheticLambda8
            @Override // androidx.core.view.OnApplyWindowInsetsListener
            public final WindowInsetsCompat onApplyWindowInsets(View view, WindowInsetsCompat windowInsetsCompat) {
                return ServerSetupActivity.lambda$onCreate$0(view, windowInsetsCompat);
            }
        });
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(24), dp(24), dp(24), dp(32));
        scrollView.addView(linearLayout);
        setContentView(scrollView);
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText("‹  Назад к VPS");
        button.setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.ServerSetupActivity$$ExternalSyntheticLambda9
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.m1748lambda$onCreate$1$rureplyaiandroidServerSetupActivity(view);
            }
        });
        add(linearLayout, button);
        text(linearLayout, "Установить / обновить сервер", 26);
        text(linearLayout, "Debian 12 или Ubuntu 24.04, публичный IPv4 и SSH-доступ от root. При первом запуске приложение настроит автоответчик и HTTPS. При обновлении Reply AI сохранит подключение Telegram, чаты и ключи API.", 15);
        EditText editTextInput = input(linearLayout, "IPv4 VPS", this.prefs.getString("ip", ""), false, 15);
        this.ip = editTextInput;
        editTextInput.setInputType(3);
        EditText editTextInput2 = input(linearLayout, "SSH-порт", this.prefs.getString("port", "22"), false, 5);
        this.port = editTextInput2;
        editTextInput2.setInputType(2);
        this.password = input(linearLayout, "Root-пароль из кабинета хостинга", "", true, 1024);
        text(linearLayout, "Пароль используется только для этой установки и не сохраняется на телефоне. Вводите новый пароль, если прежний был виден на скриншоте.", 13);
        EditText editTextInput3 = input(linearLayout, "Свой домен — необязательно", this.prefs.getString("domain", ""), false, 253);
        this.domain = editTextInput3;
        editTextInput3.setInputType(17);
        this.domain.setHint("Пусто — автоматический адрес sslip.io");
        text(linearLayout, "Для автоматического HTTPS используется бесплатное имя sslip.io, связанное с IP. Свой домен должен указывать на VPS. В кабинете хостинга должны быть разрешены входящие TCP 80 и 443.", 13);
        text(linearLayout, "Во время первой установки оставьте этот экран открытым. Если связь прервётся, введите пароль снова и продолжите: уже запущенная на VPS установка не зависит от телефона.", 13);
        Button button2 = new Button(this);
        this.start = button2;
        button2.setAllCaps(false);
        this.start.setTextColor(ThemeManager.colors(this).accent);
        refreshButton();
        add(linearLayout, this.start);
        this.start.setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.ServerSetupActivity$$ExternalSyntheticLambda10
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.m1749lambda$onCreate$2$rureplyaiandroidServerSetupActivity(view);
            }
        });
        ProgressBar progressBar = new ProgressBar(this);
        this.progress = progressBar;
        progressBar.setVisibility(8);
        add(linearLayout, this.progress);
        TextView textViewText = text(linearLayout, this.prefs.getString("job", "").isEmpty() ? "Установка ещё не начата." : "Есть незавершённая установка. Введите root-пароль и продолжите проверку.", 15);
        this.status = textViewText;
        textViewText.setTextIsSelectable(true);
        Button button3 = new Button(this);
        this.done = button3;
        button3.setAllCaps(false);
        this.done.setText("Вернуться к управлению");
        this.done.setVisibility(8);
        this.done.setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.ServerSetupActivity$$ExternalSyntheticLambda11
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.m1750lambda$onCreate$3$rureplyaiandroidServerSetupActivity(view);
            }
        });
        add(linearLayout, this.done);
        Button button4 = new Button(this);
        button4.setAllCaps(false);
        button4.setText("Сохранить диагностику");
        button4.setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.ServerSetupActivity$$ExternalSyntheticLambda12
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.m1751lambda$onCreate$4$rureplyaiandroidServerSetupActivity(view);
            }
        });
        add(linearLayout, button4);
    }

    static /* synthetic */ WindowInsetsCompat lambda$onCreate$0(View view, WindowInsetsCompat windowInsetsCompat) {
        Insets insets = windowInsetsCompat.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
        view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
        return windowInsetsCompat;
    }
    /* synthetic */ void m1748lambda$onCreate$1$rureplyaiandroidServerSetupActivity(View view) {
        finish();
    }
    /* synthetic */ void m1749lambda$onCreate$2$rureplyaiandroidServerSetupActivity(View view) {
        startClicked();
    }
    /* synthetic */ void m1750lambda$onCreate$3$rureplyaiandroidServerSetupActivity(View view) {
        finish();
    }
    /* synthetic */ void m1751lambda$onCreate$4$rureplyaiandroidServerSetupActivity(View view) {
        startActivity(new Intent(this, (Class<?>) DiagnosticsActivity.class));
    }

    private void refreshButton() {
        Button button = this.start;
        if (button != null) {
            button.setText(this.prefs.getString("job", "").isEmpty() ? "Установить и подключить" : "Продолжить установку");
        }
    }

    private void startClicked() {
        if (this.busy) {
            return;
        }
        try {
            SshInstaller.Spec spec = new SshInstaller.Spec(this.ip.getText().toString(), this.port.getText().toString(), this.domain.getText().toString());
            if (this.password.getText().length() == 0) {
                throw new Exception("Введите root-пароль сервера.");
            }
            byte[] bytes = this.password.getText().toString().getBytes(StandardCharsets.UTF_8);
            this.password.setText("");
            setBusy(true);
            this.status.setText("Подключаемся к VPS по SSH…");
            if (DiagnosticLog.get() != null) {
                DiagnosticLog.get().clearServerCache();
            }
            DiagnosticLog.event("install", "connect", null);
            submit(spec, bytes);
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void submit(final SshInstaller.Spec spec, final byte[] bArr) {
        final String string = this.prefs.getString("pin:" + spec.identity(), "");
        final byte[] bArr2 = string.isEmpty() ? (byte[]) bArr.clone() : null;
        this.worker.execute(new Runnable() { // from class: ru.replyai.android.ServerSetupActivity$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.m1754lambda$submit$12$rureplyaiandroidServerSetupActivity(spec, bArr, string, bArr2);
            }
        });
    }
    /* synthetic */ void m1754lambda$submit$12$rureplyaiandroidServerSetupActivity(final SshInstaller.Spec spec, byte[] bArr, String str, final byte[] bArr2) {
        boolean z;
        try {
            try {
                SshInstaller sshInstaller2 = new SshInstaller();
                try {
                    this.active = sshInstaller2;
                    sshInstaller2.connect(spec, bArr, str);
                    if (bArr2 != null) {
                        Arrays.fill(bArr2, (byte) 0);
                    }
                    update("Проверяем ОС и доступ root…");
                    DiagnosticLog.event("install", "preflight", null);
                    sshInstaller2.preflight();
                    String string = this.prefs.getString("job", "");
                    if (!BuildConfig.VERSION_NAME.equals(this.prefs.getString("job_version", ""))) {
                        string = "";
                    }
                    if (!spec.identity().equals(this.prefs.getString("job_server", "")) || !spec.domain.equals(this.prefs.getString("job_domain", ""))) {
                        string = "";
                    }
                    if (string.isEmpty()) {
                        string = UUID.randomUUID().toString().replace("-", "");
                    }
                    if (!this.prefs.edit().putString("ip", spec.ip).putString("port", String.valueOf(spec.port)).putString("domain", domainValue(spec)).putString("job", string).putString("job_version", BuildConfig.VERSION_NAME).putString("job_server", spec.identity()).putString("job_domain", spec.domain).commit()) {
                        throw new Exception("Не удалось сохранить этап установки на телефоне.");
                    }
                    if (sshInstaller2.state(string).equals("MISSING")) {
                        update("Загружаем серверную часть на VPS…");
                        DiagnosticLog.event("install", "upload", null);
                        InputStream inputStreamOpen = getAssets().open("replyai-server.bundle");
                        try {
                            sshInstaller2.begin(spec, string, inputStreamOpen);
                            if (inputStreamOpen != null) {
                                inputStreamOpen.close();
                            }
                        } catch (Throwable th) {
                            if (inputStreamOpen == null) {
                                throw th;
                            }
                            try {
                                inputStreamOpen.close();
                                throw th;
                            } catch (Throwable th2) {
                                th.addSuppressed(th2);
                                throw th;
                            }
                        }
                    }
                    long deadline = System.currentTimeMillis() + 1920000;
                    String last = "";
                    while (!Thread.currentThread().isInterrupted()) {
                        String state = sshInstaller2.state(string);
                        if (!state.equals(last)) {
                            DiagnosticLog.stage(state);
                            last = state;
                        }
                        if (state.equals("DONE") || state.equals("HTTPS_PENDING")) {
                            collectDiagnostics(sshInstaller2, string);
                            String str3 = sshInstaller2.token();
                            new RemoteSettings(this).save(spec.url(), str3);
                            this.prefs.edit().remove("job").commit();
                            update("Подключение сохранено. Проверяем HTTPS…");
                            DiagnosticLog.event("install", "https_check", null);
                            try {
                                new RemoteClient(spec.url(), str3).call("GET", "/api/status", null);
                                z = true;
                            } catch (Exception unused) {
                                z = false;
                            }
                            DiagnosticLog.event("install", "complete", null);
                            final boolean httpsOk = z;
                            this.ui.post(() -> m1755lambda$submit$5$rureplyaiandroidServerSetupActivity(httpsOk, spec));
                            sshInstaller2.close();
                            this.active = null;
                            Arrays.fill(bArr, (byte) 0);
                            return;
                        }
                        if (state.equals("FAILED") || state.equals("BUSY")) {
                            collectDiagnostics(sshInstaller2, string);
                            this.prefs.edit().remove("job").commit();
                            throw new Exception(state.equals("BUSY") ? "На VPS уже идёт другая установка. Дождитесь её окончания и повторите." : "Установка на VPS завершилась с ошибкой. Проверьте свободное место и доступ сервера к пакетам Debian и Python, затем повторите установку.");
                        }
                        if (state.equals("MISSING")) {
                            throw new Exception("Установка была прервана на VPS. Нажмите продолжить, чтобы запустить её повторно.");
                        }
                        if (System.currentTimeMillis() > deadline) {
                            throw new Exception("Проверка установки заняла слишком много времени. Повторно подключитесь к VPS.");
                        }
                        update(state.equals("HTTPS") ? "Получаем HTTPS-сертификат. Обычно это занимает несколько минут…"
                                : state.equals("CHECKING") ? "Проверяем запуск автоответчика…" : "Устанавливаем пакеты и службу автоответчика на VPS…");
                        Thread.sleep(3000L);
                    }
                    throw new InterruptedException();
                } catch (Throwable th3) {
                    try {
                        sshInstaller2.close();
                        throw th3;
                    } catch (Throwable th4) {
                        th3.addSuppressed(th4);
                        throw th3;
                    }
                }
            } catch (Throwable th5) {
                this.active = null;
                Arrays.fill(bArr, (byte) 0);
                throw th5;
            }
        } catch (SshInstaller.TrustRequired e) {
            this.retryPassword = bArr2;
            this.ui.post(new Runnable() { // from class: ru.replyai.android.ServerSetupActivity$$ExternalSyntheticLambda6
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.m1752lambda$submit$10$rureplyaiandroidServerSetupActivity(bArr2, spec, e);
                }
            });
        } catch (Exception e2) {
            DiagnosticLog.event("install", "failed", e2);
            if (bArr2 != null) {
                Arrays.fill(bArr2, (byte) 0);
            }
            final String message = e2.getClass() == Exception.class ? e2.getMessage() : "Соединение прервано. Проверьте IPv4, SSH-порт, root-пароль и интернет. Если установка уже запущена на VPS, она продолжится. Введите пароль снова и нажмите продолжить.";
            this.ui.post(new Runnable() { // from class: ru.replyai.android.ServerSetupActivity$$ExternalSyntheticLambda7
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.m1753lambda$submit$11$rureplyaiandroidServerSetupActivity(message);
                }
            });
        }
    }
    /* synthetic */ void m1755lambda$submit$5$rureplyaiandroidServerSetupActivity(boolean z, SshInstaller.Spec spec) {
        StringBuilder sbAppend;
        String str;
        if (alive()) {
            setResult(-1);
            setBusy(false);
            this.done.setVisibility(0);
            TextView textView = this.status;
            if (z) {
                sbAppend = new StringBuilder("Готово! Сервер установлен и отвечает по HTTPS.\n").append(spec.url());
                str = "\n\nВернитесь к управлению, подключите Telegram и настройте AI API.";
            } else {
                sbAppend = new StringBuilder("Сервер установлен, подключение сохранено. HTTPS пока недоступен.\n").append(spec.url());
                str = "\n\nПроверьте в кабинете хостинга входящие TCP 80/443 и DNS. Сертификат может появиться через несколько минут. Вернитесь к управлению и нажмите «Обновить».";
            }
            textView.setText(sbAppend.append(str).toString());
            this.start.setVisibility(8);
        }
    }
    /* synthetic */ void m1752lambda$submit$10$rureplyaiandroidServerSetupActivity(byte[] bArr, final SshInstaller.Spec spec, final SshInstaller.TrustRequired trustRequired) {
        if (alive()) {
            new AlertDialog.Builder(this).setTitle("Первое подключение к VPS").setMessage("Сервер " + spec.identity() + " предъявил SSH-ключ:\n\n" + trustRequired.fingerprint + "\n\nЕсли хостинг показывает отпечаток ключа, сверьте его. Подтвердите доверие именно своему серверу. При изменении ключа приложение заблокирует вход.").setNegativeButton("Отмена", new DialogInterface.OnClickListener() { // from class: ru.replyai.android.ServerSetupActivity$$ExternalSyntheticLambda1
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    this.f$0.m1756lambda$submit$6$rureplyaiandroidServerSetupActivity(dialogInterface, i);
                }
            }).setPositiveButton("Доверять серверу", new DialogInterface.OnClickListener() { // from class: ru.replyai.android.ServerSetupActivity$$ExternalSyntheticLambda2
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    this.f$0.m1757lambda$submit$7$rureplyaiandroidServerSetupActivity(spec, trustRequired, dialogInterface, i);
                }
            }).setOnCancelListener(new DialogInterface.OnCancelListener() { // from class: ru.replyai.android.ServerSetupActivity$$ExternalSyntheticLambda3
                @Override // android.content.DialogInterface.OnCancelListener
                public final void onCancel(DialogInterface dialogInterface) {
                    this.f$0.m1758lambda$submit$8$rureplyaiandroidServerSetupActivity(dialogInterface);
                }
            }).setOnDismissListener(new DialogInterface.OnDismissListener() { // from class: ru.replyai.android.ServerSetupActivity$$ExternalSyntheticLambda4
                @Override // android.content.DialogInterface.OnDismissListener
                public final void onDismiss(DialogInterface dialogInterface) {
                    this.f$0.m1759lambda$submit$9$rureplyaiandroidServerSetupActivity(dialogInterface);
                }
            }).show();
        } else if (bArr != null) {
            Arrays.fill(bArr, (byte) 0);
        }
    }
    /* synthetic */ void m1756lambda$submit$6$rureplyaiandroidServerSetupActivity(DialogInterface dialogInterface, int i) {
        setBusy(false);
        this.status.setText("Подключение отменено.");
    }
    /* synthetic */ void m1757lambda$submit$7$rureplyaiandroidServerSetupActivity(SshInstaller.Spec spec, SshInstaller.TrustRequired trustRequired, DialogInterface dialogInterface, int i) {
        if (!this.prefs.edit().putString("pin:" + spec.identity(), trustRequired.fingerprint).commit()) {
            setBusy(false);
            showError("Не удалось сохранить SSH-ключ.");
            return;
        }
        byte[] bArr = this.retryPassword;
        this.retryPassword = null;
        if (bArr != null) {
            submit(spec, bArr);
        } else {
            setBusy(false);
        }
    }
    /* synthetic */ void m1758lambda$submit$8$rureplyaiandroidServerSetupActivity(DialogInterface dialogInterface) {
        setBusy(false);
        this.status.setText("Подключение отменено.");
    }
    /* synthetic */ void m1759lambda$submit$9$rureplyaiandroidServerSetupActivity(DialogInterface dialogInterface) {
        if (this.retryPassword != null) {
            Arrays.fill(this.retryPassword, (byte) 0);
            this.retryPassword = null;
        }
    }
    /* synthetic */ void m1753lambda$submit$11$rureplyaiandroidServerSetupActivity(String str) {
        if (alive()) {
            setBusy(false);
            this.status.setText(str);
            refreshButton();
        }
    }

    private String domainValue(SshInstaller.Spec spec) {
        return spec.domain.equals(new StringBuilder().append(spec.ip.replace('.', '-')).append(".sslip.io").toString()) ? "" : spec.domain;
    }

    private void collectDiagnostics(SshInstaller sshInstaller, String str) {
        try {
            if (DiagnosticLog.get() != null) {
                DiagnosticLog.get().cacheSetup(sshInstaller.diagnostics(str));
            }
        } catch (Exception e) {
            DiagnosticLog.event("install", "collect", e);
        }
    }

    private void setBusy(boolean z) {
        this.busy = z;
        this.start.setEnabled(!z);
        this.ip.setEnabled(!z);
        this.port.setEnabled(!z);
        this.password.setEnabled(!z);
        this.domain.setEnabled(!z);
        this.progress.setVisibility(z ? 0 : 8);
        if (z) {
            getWindow().addFlags(128);
        } else {
            getWindow().clearFlags(128);
        }
        refreshButton();
    }

    private void update(final String str) {
        this.ui.post(new Runnable() { // from class: ru.replyai.android.ServerSetupActivity$$ExternalSyntheticLambda13
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.m1760lambda$update$13$rureplyaiandroidServerSetupActivity(str);
            }
        });
    }
    /* synthetic */ void m1760lambda$update$13$rureplyaiandroidServerSetupActivity(String str) {
        if (alive()) {
            this.status.setText(str);
        }
    }

    private boolean alive() {
        return (isFinishing() || isDestroyed()) ? false : true;
    }

    private void showError(String str) {
        new AlertDialog.Builder(this).setTitle("Проверьте данные").setMessage(str).setPositiveButton("Понятно", (DialogInterface.OnClickListener) null).show();
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        if (this.retryPassword != null) {
            Arrays.fill(this.retryPassword, (byte) 0);
        }
        SshInstaller sshInstaller = this.active;
        if (sshInstaller != null) {
            sshInstaller.close();
        }
        this.worker.shutdownNow();
        this.ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }

    private void add(LinearLayout linearLayout, View view) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = dp(12);
        linearLayout.addView(view, layoutParams);
    }

    private TextView text(LinearLayout linearLayout, String str, int i) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextSize(i);
        textView.setTextColor(ThemeManager.colors(this).ink);
        add(linearLayout, textView);
        return textView;
    }

    private EditText input(LinearLayout linearLayout, String str, String str2, boolean z, int i) {
        text(linearLayout, str, 13);
        EditText editText = new EditText(this);
        editText.setSingleLine(true);
        editText.setTextSize(16.0f);
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(i)});
        editText.setInputType((z ? 128 : 0) | 1);
        editText.setImportantForAutofill(2);
        editText.setSaveEnabled(false);
        editText.setText(str2);
        add(linearLayout, editText);
        return editText;
    }
}
