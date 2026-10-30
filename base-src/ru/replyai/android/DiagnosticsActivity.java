package ru.replyai.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;
public final class DiagnosticsActivity extends Activity {
    static final int SAVE_REPORT = 73;
    private byte[] pending;
    private Button save;
    private TextView status;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override // android.app.Activity
    public void onCreate(Bundle bundle) {
        ThemeManager.apply(this);
        super.onCreate(bundle);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(ThemeManager.colors(this).background);
        ViewCompat.setOnApplyWindowInsetsListener(scrollView, new OnApplyWindowInsetsListener() { // from class: ru.replyai.android.DiagnosticsActivity$$ExternalSyntheticLambda4
            @Override // androidx.core.view.OnApplyWindowInsetsListener
            public final WindowInsetsCompat onApplyWindowInsets(View view, WindowInsetsCompat windowInsetsCompat) {
                return DiagnosticsActivity.lambda$onCreate$0(view, windowInsetsCompat);
            }
        });
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        int iRound = Math.round(getResources().getDisplayMetrics().density * 24.0f);
        linearLayout.setPadding(iRound, iRound, iRound, iRound);
        scrollView.addView(linearLayout);
        setContentView(scrollView);
        text(linearLayout, "Диагностика", 26);
        text(linearLayout, "Сохраните отчёт после ошибки и прикрепите JSON-файл в этот чат для разбора. Отчёт содержит время, этапы подключения, типы ошибок и состояние служб. Пароли, ключи, коды входа, номера телефонов и тексты переписки в него не включаются.", 16);
        text(linearLayout, "Приложение попробует получить журнал VPS. Если сервер недоступен, сохранятся события телефона и последний полученный отчёт сервера с отметкой «cached». Файл никуда не отправляется автоматически.", 14);
        TextView textViewText = text(linearLayout, "Нажмите кнопку, затем выберите папку для файла.", 15);
        this.status = textViewText;
        textViewText.setTextIsSelectable(true);
        Button button = new Button(this);
        this.save = button;
        button.setAllCaps(false);
        this.save.setText("Сохранить диагностику");
        linearLayout.addView(this.save);
        this.save.setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.DiagnosticsActivity$$ExternalSyntheticLambda5
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                DiagnosticsActivity.this.m1637lambda$onCreate$1$rureplyaiandroidDiagnosticsActivity(view);
            }
        });
        Button button2 = new Button(this);
        button2.setAllCaps(false);
        button2.setText("Назад");
        linearLayout.addView(button2);
        button2.setOnClickListener(new View.OnClickListener() { // from class: ru.replyai.android.DiagnosticsActivity$$ExternalSyntheticLambda6
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                DiagnosticsActivity.this.m1638lambda$onCreate$2$rureplyaiandroidDiagnosticsActivity(view);
            }
        });
        if (bundle != null) {
            this.status.setText("Если сохранение было прервано, нажмите кнопку ещё раз.");
        }
    }

    static /* synthetic */ WindowInsetsCompat lambda$onCreate$0(View view, WindowInsetsCompat windowInsetsCompat) {
        Insets insets = windowInsetsCompat.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
        view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
        return windowInsetsCompat;
    }
    /* synthetic */ void m1637lambda$onCreate$1$rureplyaiandroidDiagnosticsActivity(View view) {
        prepare();
    }
    /* synthetic */ void m1638lambda$onCreate$2$rureplyaiandroidDiagnosticsActivity(View view) {
        finish();
    }

    private TextView text(LinearLayout linearLayout, String str, int i) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextSize(i);
        textView.setTextColor(ThemeManager.colors(this).ink);
        textView.setPadding(0, 12, 0, 20);
        linearLayout.addView(textView);
        return textView;
    }

    private void prepare() {
        this.save.setEnabled(false);
        this.status.setText("Собираем диагностику. Проверка сервера может занять до 30 секунд…");
        RemoteSettings remoteSettings = new RemoteSettings(this);
        final String strUrl = remoteSettings.url();
        final String str = remoteSettings.token();
        this.worker.execute(new Runnable() { // from class: ru.replyai.android.DiagnosticsActivity$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                DiagnosticsActivity.this.m1641lambda$prepare$5$rureplyaiandroidDiagnosticsActivity(strUrl, str);
            }
        });
    }
    /* synthetic */ void m1641lambda$prepare$5$rureplyaiandroidDiagnosticsActivity(String str, String str2) {
        try {
            JSONObject jSONObject = null;
            if (!str.isEmpty() && !str2.isEmpty()) {
                try {
                    JSONObject jSONObjectCall = new RemoteClient(str, str2).call("GET", "/api/diagnostics", null);
                    if (DiagnosticLog.safeServer(jSONObjectCall) == null) {
                        throw new IllegalStateException();
                    }
                    DiagnosticLog.get().cacheServer(jSONObjectCall);
                    jSONObject = jSONObjectCall;
                } catch (Exception e) {
                    DiagnosticLog.event("export", "collect", e);
                }
            }
            JSONObject jSONObjectReport = DiagnosticLog.get().report(jSONObject);
            final byte[] bytes = jSONObjectReport.toString(2).getBytes(StandardCharsets.UTF_8);
            final String string = jSONObjectReport.getString("server_source");
            this.ui.post(new Runnable() { // from class: ru.replyai.android.DiagnosticsActivity$$ExternalSyntheticLambda2
                @Override // java.lang.Runnable
                public final void run() {
                    DiagnosticsActivity.this.m1639lambda$prepare$3$rureplyaiandroidDiagnosticsActivity(bytes, string);
                }
            });
        } catch (Exception e2) {
            DiagnosticLog.event("export", "prepare", e2);
            this.ui.post(new Runnable() { // from class: ru.replyai.android.DiagnosticsActivity$$ExternalSyntheticLambda3
                @Override // java.lang.Runnable
                public final void run() {
                    DiagnosticsActivity.this.m1640lambda$prepare$4$rureplyaiandroidDiagnosticsActivity();
                }
            });
        }
    }
    /* synthetic */ void m1639lambda$prepare$3$rureplyaiandroidDiagnosticsActivity(byte[] bArr, String str) {
        String str2;
        if (isFinishing() || isDestroyed()) {
            return;
        }
        this.pending = bArr;
        TextView textView = this.status;
        if (str.equals("fresh")) {
            str2 = "Получена свежая диагностика телефона и VPS. Выберите, куда сохранить файл.";
        } else {
            str2 = str.equals("cached") ? "VPS сейчас недоступен. Включены события телефона и сохранённый ранее отчёт сервера с его датой." : "Диагностика телефона готова. Отчёт VPS недоступен: проверьте подключение и обновите сервер до версии 2.4.";
        }
        textView.setText(str2);
        try {
            startActivityForResult(documentIntent(), SAVE_REPORT);
        } catch (Exception e) {
            this.pending = null;
            this.save.setEnabled(true);
            this.status.setText("Не удалось открыть выбор файла. Проверьте системное приложение «Файлы».");
            DiagnosticLog.event("export", "save", e);
        }
    }
    /* synthetic */ void m1640lambda$prepare$4$rureplyaiandroidDiagnosticsActivity() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        this.save.setEnabled(true);
        this.status.setText("Не удалось подготовить отчёт. Перезапустите приложение и повторите.");
    }

    static Intent documentIntent() {
        return new Intent("android.intent.action.CREATE_DOCUMENT").addCategory("android.intent.category.OPENABLE").setType("application/json").putExtra("android.intent.extra.TITLE", "ReplyAI-diagnostics-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now()) + ".json");
    }

    @Override // android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if (i != SAVE_REPORT) {
            return;
        }
        this.save.setEnabled(true);
        if (i2 != -1 || intent == null || intent.getData() == null) {
            this.pending = null;
            this.status.setText("Сохранение отменено. Можно попробовать снова.");
            DiagnosticLog.event("export", "cancel", null);
            return;
        }
        final byte[] bArr = this.pending;
        if (bArr == null) {
            this.status.setText("Приложение было перезапущено. Соберите отчёт ещё раз.");
            return;
        }
        this.pending = null;
        final Uri data = intent.getData();
        this.save.setEnabled(false);
        this.status.setText("Сохраняем файл…");
        this.worker.execute(new Runnable() { // from class: ru.replyai.android.DiagnosticsActivity$$ExternalSyntheticLambda7
            @Override // java.lang.Runnable
            public final void run() {
                DiagnosticsActivity.this.m1636lambda$onActivityResult$7$rureplyaiandroidDiagnosticsActivity(data, bArr);
            }
        });
    }
    /* synthetic */ void m1636lambda$onActivityResult$7$rureplyaiandroidDiagnosticsActivity(Uri uri, byte[] bArr) {
        boolean ok = false;
        try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
            if (out == null) throw new IOException();
            out.write(bArr);
            out.flush();
            ok = true;
        } catch (Exception e) {
            DiagnosticLog.event("export", "save", e);
        }
        final boolean saved = ok;
        this.ui.post(() -> m1635lambda$onActivityResult$6$rureplyaiandroidDiagnosticsActivity(saved));
    }
    /* synthetic */ void m1635lambda$onActivityResult$6$rureplyaiandroidDiagnosticsActivity(boolean z) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        this.save.setEnabled(true);
        this.status.setText(z ? "Отчёт сохранён. Прикрепите JSON-файл в этот чат после воспроизведения ошибки." : "Не удалось записать файл. Выберите другую папку и повторите.");
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        this.worker.shutdownNow();
        this.ui.removeCallbacksAndMessages(null);
        this.pending = null;
        super.onDestroy();
    }
}
