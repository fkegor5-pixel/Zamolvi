package ru.replyai.android;

import android.content.Context;
import android.os.Build;
import android.util.AtomicFile;
import androidx.core.app.NotificationCompat;
import androidx.core.os.EnvironmentCompat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
public final class DiagnosticLog {
    static final int LIMIT = 200;
    private static volatile DiagnosticLog current;
    private JSONObject cachedServer;
    private JSONArray events;
    private final AtomicFile file;
    private JSONObject setup;
    private static final Set<String> KINDS = values("api", "http", "ai", "telegram", "worker", "voice", "install", "app", "crash", "export", "notification");
    private static final Set<String> OPERATIONS = values("/api/status", "/api/settings", "/api/control", "/api/telegram/start", "/api/telegram/code", "/api/telegram/password", "/api/telegram/resend", "/api/telegram/logout", "/api/chats", "/api/behavior", "/api/voice", "/api/voice/clear", "/api/stats", "/api/formatting", "/api/test", "/api/clear", "/api/diagnostics", "restore", "code_delivery", "answer_failed", "transcribe_failed", "download_failed", "send", "send_ambiguous", "/api/telegram/qr", "/api/telegram/qr/start", "/api/telegram/qr/cancel", "qr_created", "qr_password", "qr_authorized", "qr_failed", "qr_revoke_failed", "qr_expired", "qr_cancelled", "reconnect", "failed", "timeout", "oauth", "oauth_network", "response", "network", "connect", "preflight", "upload", "stage", "https_check", "complete", "collect", "uncaught", "started", "prepare", "save", "cancel", "other");
    private static final Set<String> STAGES = values("none", "code", "password", "blocked", "authorized", "idle", EnvironmentCompat.MEDIA_UNKNOWN, "expired", "disconnected", "qr", "failed", "MISSING", "QUEUED", "INSTALLING", "CHECKING", "HTTPS", "DONE", "HTTPS_PENDING", "FAILED", "BUSY");
    private static final Set<String> DELIVERY = values("telegram", "sms", NotificationCompat.CATEGORY_CALL, "flash_call", NotificationCompat.CATEGORY_MISSED_CALL, NotificationCompat.CATEGORY_EMAIL, "email_setup", "fragment", "sms_word", "sms_phrase", "official_app", EnvironmentCompat.MEDIA_UNKNOWN, "none", "unsupported", "payment_required", "qr");
    private static final Set<String> FILES = values("engine.py", "web.py", "ai.py", "store.py", "__main__.py", "diagnostics.py", "DiagnosticLog.java", "ReplyAiApplication.java", "RemoteClient.java", "MainActivity.java", "SshInstaller.java", "RemoteSettings.java", "ServerSetupActivity.java", "DiagnosticsActivity.java", "QrLoginActivity.java", "QrCodeView.java", "qr.py", "voice.py", "stats.py", "queueing.py", "quiet.py", "formatting.py", "ThemeManager.java", "StatsChartView.java");
    private static final Set<String> PY_FUNCTIONS = values("login_start", "login_code", "login_resend", "login_password", "logout", "login_info", "accept_sent_code", "_accept_sent_code", "connect", "_connect", "start", "stop", "run", "process_one", "prepare_parts", "transcribe", "enqueue", "save_voice", "save_behavior", "statistics", "put_voice", "put_behavior", "clear_voice", "next_job", "work_allowed", "source_matches", "sources_unchanged", "answer", "_answer", "giga_token", "_gigachat_token", "_chat", "_request", "save_settings", "chats", "select", "enable", "disable", "record_error", "limit_body", "authenticate", "login_limit", "login", "code", "password", "resend", "test", "diagnostics", NotificationCompat.CATEGORY_STATUS, "put_settings", "settings", "control", "health", "clear", "read", "update", "_write", "emit", "report", "_set", "export_locked", "expire_password", "cancel_locked", "cancel", "discard_locked", "password", "authorized", "failed", "qr_start", "qr_state", "qr_cancel");

    private static Set<String> values(String... strArr) {
        return new HashSet(Arrays.asList(strArr));
    }

    DiagnosticLog(Context context) {
        this.events = new JSONArray();
        AtomicFile atomicFile = new AtomicFile(new File(context.getFilesDir(), "diagnostics-v1.json"));
        this.file = atomicFile;
        try {
            FileInputStream fileInputStreamOpenRead = atomicFile.openRead();
            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byte[] bArr = new byte[4096];
                while (true) {
                    int i = fileInputStreamOpenRead.read(bArr);
                    if (i == -1) {
                        JSONObject jSONObject = new JSONObject(new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8));
                        this.events = safeEvents(jSONObject.optJSONArray("events"), LIMIT);
                        this.cachedServer = safeServer(jSONObject.optJSONObject("server"));
                        this.setup = safeSetup(jSONObject.optJSONObject("setup"));
                        if (fileInputStreamOpenRead != null) {
                            break;
                        } else {
                            return;
                        }
                    }
                    if (byteArrayOutputStream.size() + i > 300000) {
                        if (fileInputStreamOpenRead != null) {
                            break;
                        } else {
                            return;
                        }
                    }
                    byteArrayOutputStream.write(bArr, 0, i);
                }
                fileInputStreamOpenRead.close();
            } catch (Throwable th) {
                if (fileInputStreamOpenRead != null) {
                    try {
                        fileInputStreamOpenRead.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        } catch (Exception unused) {
        }
    }

    static synchronized void initialize(Context context) {
        current = new DiagnosticLog(context.getApplicationContext());
        RemoteClient.observe(new RemoteClient.RequestObserver() { // from class: ru.replyai.android.DiagnosticLog$$ExternalSyntheticLambda0
            @Override // ru.replyai.android.RemoteClient.RequestObserver
            public final void complete(String str, int i, long j, String str2, Throwable th) {
                DiagnosticLog.http(str, i, j, str2, th);
            }
        });
    }

    static DiagnosticLog get() {
        return current;
    }

    public static void event(String str, String str2, Throwable th) {
        DiagnosticLog diagnosticLog = current;
        if (diagnosticLog != null) {
            diagnosticLog.append(str, str2, th, 0, 0L, "", "");
        }
    }

    public static void stage(String str) {
        DiagnosticLog diagnosticLog = current;
        if (diagnosticLog != null) {
            diagnosticLog.append("install", "stage", null, 0, 0L, "", str);
        }
    }

    public static void http(String str, int i, long j, String str2, Throwable th) {
        DiagnosticLog diagnosticLog;
        if (((str.equals("/api/status") || str.equals("/api/telegram/qr")) && i == LIMIT && th == null) || (diagnosticLog = current) == null) {
            return;
        }
        diagnosticLog.append("http", str, th, i, j, str2, "");
    }

    private synchronized void append(String str, String str2, Throwable th, int i, long j, String str3, String str4) {
        try {
            JSONObject jSONObjectPut = new JSONObject().put("at", Instant.now().toString()).put("kind", choice(str, KINDS)).put("operation", choice(str2, OPERATIONS));
            if (i > 0) {
                jSONObjectPut.put("http_status", i);
            }
            if (j > 0) {
                jSONObjectPut.put("duration_ms", Math.min(j, 2147483647L));
            }
            if (str3 != null && str3.matches("[a-f0-9]{32}")) {
                jSONObjectPut.put("request_id", str3);
            }
            if (!str4.isEmpty()) {
                jSONObjectPut.put("stage", choice(str4, STAGES));
            }
            if (th != null) {
                jSONObjectPut.put("error_type", errorClass(th.getClass().getSimpleName()));
                JSONArray jSONArray = new JSONArray();
                JSONArray jSONArray2 = new JSONArray();
                int i2 = 0;
                while (th != null && i2 < 5) {
                    if (i2 > 0) {
                        jSONArray.put(errorClass(th.getClass().getSimpleName()));
                    }
                    for (StackTraceElement stackTraceElement : th.getStackTrace()) {
                        if (jSONArray2.length() >= 8) {
                            break;
                        }
                        if (stackTraceElement.getClassName().startsWith("ru.replyai.android.") && FILES.contains(stackTraceElement.getFileName())) {
                            jSONArray2.put(new JSONObject().put("file", stackTraceElement.getFileName()).put("function", safeJavaFunction(stackTraceElement.getMethodName())).put("line", Math.max(0, stackTraceElement.getLineNumber())));
                        }
                    }
                    i2++;
                    th = th.getCause();
                }
                jSONObjectPut.put("causes", jSONArray).put("frames", jSONArray2);
            }
            this.events.put(jSONObjectPut);
            this.events = safeEvents(this.events, LIMIT);
            persist();
        } catch (Exception unused) {
        }
    }

    synchronized void cacheServer(JSONObject jSONObject) {
        this.cachedServer = safeServer(jSONObject);
        persist();
    }

    synchronized void clearServerCache() {
        this.cachedServer = null;
        this.setup = null;
        persist();
    }

    synchronized void cacheSetup(JSONObject jSONObject) {
        this.setup = safeSetup(jSONObject);
        persist();
    }

    synchronized JSONObject report(JSONObject jSONObject) throws Exception {
        String str;
        JSONObject jSONObjectPut;
        JSONObject jSONObjectSafeServer = safeServer(jSONObject);
        JSONObject jSONObjectPut2 = new JSONObject().put("schema", "replyai.android-diagnostics.v1").put("generated_at", Instant.now().toString()).put("report_id", UUID.randomUUID().toString().replace("-", "")).put("app", new JSONObject().put("version", BuildConfig.VERSION_NAME).put("version_code", 9).put("android_sdk", Build.VERSION.SDK_INT).put("manufacturer", deviceLabel(Build.MANUFACTURER)).put("model", deviceLabel(Build.MODEL)));
        if (jSONObjectSafeServer != null) {
            str = "fresh";
        } else {
            str = this.cachedServer != null ? "cached" : "unavailable";
        }
        jSONObjectPut = jSONObjectPut2.put("server_source", str).put("events", new JSONArray(this.events.toString()));
        if (jSONObjectSafeServer != null || this.cachedServer != null) {
            if (jSONObjectSafeServer == null) {
                jSONObjectSafeServer = new JSONObject(this.cachedServer.toString());
            }
            jSONObjectPut.put("server", jSONObjectSafeServer);
        }
        if (this.setup != null) {
            jSONObjectPut.put("installation", new JSONObject(this.setup.toString()));
        }
        return jSONObjectPut;
    }

    private synchronized void persist() {
        FileOutputStream fileOutputStreamStartWrite = null;
        try {
            JSONObject jSONObjectPut = new JSONObject().put("events", this.events);
            JSONObject jSONObject = this.cachedServer;
            if (jSONObject != null) {
                jSONObjectPut.put("server", jSONObject);
            }
            JSONObject jSONObject2 = this.setup;
            if (jSONObject2 != null) {
                jSONObjectPut.put("setup", jSONObject2);
            }
            fileOutputStreamStartWrite = this.file.startWrite();
            fileOutputStreamStartWrite.write(jSONObjectPut.toString().getBytes(StandardCharsets.UTF_8));
            this.file.finishWrite(fileOutputStreamStartWrite);
        } catch (Exception unused) {
            if (fileOutputStreamStartWrite != null) {
                this.file.failWrite(fileOutputStreamStartWrite);
            }
        }
    }

    private static String deviceLabel(String str) {
        return (str == null || !str.matches("[A-Za-z0-9 _.,()+/-]{1,80}")) ? EnvironmentCompat.MEDIA_UNKNOWN : str;
    }

    private static String choice(String str, Set<String> set) {
        return set.contains(str) ? str : "other";
    }

    private static String errorClass(String str) {
        return (str.matches("[A-Za-z][A-Za-z0-9]{0,70}(Exception|Error)") || str.equals("Exception") || str.equals("Error") || values("ConnectTimeout", "ReadTimeout", "WriteTimeout", "PoolTimeout").contains(str)) ? str : "OtherError";
    }

    private static String safeJavaFunction(String str) {
        return str.matches("[A-Za-z_$<>][A-Za-z0-9_$<>]{0,100}") ? str : "other";
    }

    private static boolean timestamp(String str) {
        return str.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9:.]{8,18}(Z|[+]00:00)");
    }

    private static void number(JSONObject jSONObject, JSONObject jSONObject2, String str, long j) throws Exception {
        Object objOpt = jSONObject.opt(str);
        if (objOpt instanceof Number) {
            jSONObject2.put(str, Math.max(0L, Math.min(j, ((Number) objOpt).longValue())));
        }
    }

    private static void bool(JSONObject jSONObject, JSONObject jSONObject2, String str) throws Exception {
        if (jSONObject.opt(str) instanceof Boolean) {
            jSONObject2.put(str, jSONObject.getBoolean(str));
        }
    }

    private static JSONArray safeEvents(JSONArray jSONArray, int i) throws Exception {
        JSONArray jSONArray2 = new JSONArray();
        if (jSONArray != null) {
            for (int iMax = Math.max(0, jSONArray.length() - i); iMax < jSONArray.length(); iMax++) {
                JSONObject jSONObjectOptJSONObject = jSONArray.optJSONObject(iMax);
                if (jSONObjectOptJSONObject != null) {
                    JSONObject jSONObjectPut = new JSONObject().put("kind", choice(jSONObjectOptJSONObject.optString("kind"), KINDS)).put("operation", choice(jSONObjectOptJSONObject.optString("operation"), OPERATIONS));
                    String strOptString = jSONObjectOptJSONObject.optString("at");
                    if (timestamp(strOptString)) {
                        jSONObjectPut.put("at", strOptString);
                    }
                    String strOptString2 = jSONObjectOptJSONObject.optString("request_id");
                    if (strOptString2.matches("[a-f0-9]{32}")) {
                        jSONObjectPut.put("request_id", strOptString2);
                    }
                    if (jSONObjectOptJSONObject.has("error_type")) {
                        jSONObjectPut.put("error_type", errorClass(jSONObjectOptJSONObject.optString("error_type")));
                    }
                    if (jSONObjectOptJSONObject.has("provider")) {
                        jSONObjectPut.put("provider", choice(jSONObjectOptJSONObject.optString("provider"), values("groq", "gemini", "gigachat", "custom", EnvironmentCompat.MEDIA_UNKNOWN)));
                    }
                    if (jSONObjectOptJSONObject.has("delivery")) {
                        jSONObjectPut.put("delivery", choice(jSONObjectOptJSONObject.optString("delivery"), DELIVERY));
                    }
                    if (jSONObjectOptJSONObject.has("stage")) {
                        jSONObjectPut.put("stage", choice(jSONObjectOptJSONObject.optString("stage"), STAGES));
                    }
                    String[] strArr = {"http_status", "duration_ms", "retry_after_s", "resend_after_s"};
                    for (int i2 = 0; i2 < 4; i2++) {
                        number(jSONObjectOptJSONObject, jSONObjectPut, strArr[i2], 2147483647L);
                    }
                    JSONArray jSONArrayOptJSONArray = jSONObjectOptJSONObject.optJSONArray("causes");
                    JSONArray jSONArray3 = new JSONArray();
                    if (jSONArrayOptJSONArray != null) {
                        for (int i3 = 0; i3 < Math.min(4, jSONArrayOptJSONArray.length()); i3++) {
                            jSONArray3.put(errorClass(jSONArrayOptJSONArray.optString(i3)));
                        }
                    }
                    if (jSONArray3.length() > 0) {
                        jSONObjectPut.put("causes", jSONArray3);
                    }
                    JSONArray jSONArrayOptJSONArray2 = jSONObjectOptJSONObject.optJSONArray("frames");
                    JSONArray jSONArray4 = new JSONArray();
                    if (jSONArrayOptJSONArray2 != null) {
                        for (int i4 = 0; i4 < Math.min(8, jSONArrayOptJSONArray2.length()); i4++) {
                            JSONObject jSONObjectOptJSONObject2 = jSONArrayOptJSONArray2.optJSONObject(i4);
                            if (jSONObjectOptJSONObject2 != null && FILES.contains(jSONObjectOptJSONObject2.optString("file"))) {
                                String strOptString3 = jSONObjectOptJSONObject2.optString("function");
                                String strOptString4 = jSONObjectOptJSONObject2.optString("file");
                                JSONObject jSONObjectPut2 = new JSONObject().put("file", strOptString4).put("function", strOptString4.endsWith(".py") ? choice(strOptString3, PY_FUNCTIONS) : safeJavaFunction(strOptString3));
                                number(jSONObjectOptJSONObject2, jSONObjectPut2, "line", 1000000L);
                                jSONArray4.put(jSONObjectPut2);
                            }
                        }
                    }
                    if (jSONArray4.length() > 0) {
                        jSONObjectPut.put("frames", jSONArray4);
                    }
                    jSONArray2.put(jSONObjectPut);
                }
            }
        }
        return jSONArray2;
    }

    static JSONObject safeServer(JSONObject jSONObject) {
        String str;
        if (jSONObject == null || !"replyai.diagnostics.v1".equals(jSONObject.optString("schema"))) {
            return null;
        }
        try {
            JSONObject jSONObjectPut = new JSONObject().put("schema", "replyai.diagnostics.v1");
            if (timestamp(jSONObject.optString("generated_at"))) {
                jSONObjectPut.put("generated_at", jSONObject.optString("generated_at"));
            }
            if (jSONObject.optString("report_id").matches("[a-f0-9]{32}")) {
                jSONObjectPut.put("report_id", jSONObject.optString("report_id"));
            }
            JSONObject jSONObjectOptJSONObject = jSONObject.optJSONObject("runtime");
            JSONObject jSONObject2 = new JSONObject();
            String str2 = EnvironmentCompat.MEDIA_UNKNOWN;
            int i = 4;
            if (jSONObjectOptJSONObject == null) {
                str = EnvironmentCompat.MEDIA_UNKNOWN;
            } else {
                try {
                    String[] strArr = {"server_version", "python_version", "telethon_version", "httpx_version"};
                    int i2 = 0;
                    while (i2 < i) {
                        String str3 = strArr[i2];
                        String[] strArr2 = strArr;
                        String strOptString = jSONObjectOptJSONObject.optString(str3);
                        int i3 = i2;
                        if (strOptString.matches("[0-9]{1,3}(\\.[0-9]{1,3}){1,3}([a-z0-9.-]{0,10})?") || strOptString.equals(EnvironmentCompat.MEDIA_UNKNOWN)) {
                            jSONObject2.put(str3, strOptString);
                        }
                        i2 = i3 + 1;
                        strArr = strArr2;
                        i = 4;
                    }
                    String[] strArr3 = new String[i];
                    strArr3[0] = "uptime_s";
                    strArr3[1] = "peak_rss_kib";
                    strArr3[2] = "disk_free_bytes";
                    strArr3[3] = "cpu_count";
                    int i4 = 0;
                    while (i4 < 4) {
                        number(jSONObjectOptJSONObject, jSONObject2, strArr3[i4], Long.MAX_VALUE);
                        i4++;
                        str2 = str2;
                        strArr3 = strArr3;
                    }
                    str = str2;
                    jSONObject2.put("platform", choice(jSONObjectOptJSONObject.optString("platform"), values("Linux", "Windows", "Darwin")));
                    jSONObject2.put("machine", choice(jSONObjectOptJSONObject.optString("machine"), values("x86_64", "aarch64", "AMD64", "arm64", "i686", "armv7l")));
                } catch (Exception unused) {
                    return null;
                }
            }
            jSONObjectPut.put("runtime", jSONObject2);
            JSONObject jSONObjectOptJSONObject2 = jSONObject.optJSONObject("state");
            JSONObject jSONObject3 = new JSONObject();
            if (jSONObjectOptJSONObject2 != null) {
                String[] strArr4 = {"authorized", "connected", "worker_failed", "worker_alive", "enabled", "resend_available", "ai_key_configured", "ai_endpoint_configured", "voice_enabled", "voice_uses_chat_key", "quiet_active"};
                for (int i5 = 0; i5 < 11; i5++) {
                    bool(jSONObjectOptJSONObject2, jSONObject3, strArr4[i5]);
                }
                String[] strArr5 = {"resend_after_s", "queued", "selected", "sent", "uncertain", "pause_remaining_s", "batch_seconds"};
                for (int i6 = 0; i6 < 7; i6++) {
                    number(jSONObjectOptJSONObject2, jSONObject3, strArr5[i6], 2147483647L);
                }
                jSONObject3.put("stage", choice(jSONObjectOptJSONObject2.optString("stage"), STAGES)).put("delivery", choice(jSONObjectOptJSONObject2.optString("delivery"), DELIVERY)).put("ai_provider", choice(jSONObjectOptJSONObject2.optString("ai_provider"), values("groq", "gemini", "gigachat", "custom", str)));
            }
            return jSONObjectPut.put("state", jSONObject3).put("events", safeEvents(jSONObject.optJSONArray("events"), 250));
        } catch (Exception unused2) {
            return null;
        }
    }

    static JSONObject safeSetup(JSONObject jSONObject) {
        char c;
        String str;
        String str2 = EnvironmentCompat.MEDIA_UNKNOWN;
        if (jSONObject == null || !"replyai.install-diagnostics.v1".equals(jSONObject.optString("schema"))) {
            return null;
        }
        try {
            JSONObject jSONObjectPut = new JSONObject().put("schema", "replyai.install-diagnostics.v1");
            if (timestamp(jSONObject.optString("generated_at"))) {
                jSONObjectPut.put("generated_at", jSONObject.optString("generated_at"));
            }
            jSONObjectPut.put("stage", choice(jSONObject.optString("stage"), STAGES));
            number(jSONObject, jSONObjectPut, "disk_free_bytes", Long.MAX_VALUE);
            JSONArray jSONArray = new JSONArray();
            JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("reasons");
            char c2 = '\b';
            String[] strArr = new String[8];
            strArr[0] = "DNS_RESOLUTION_FAILED";
            strArr[1] = "TLS_CERTIFICATE_ERROR";
            int i = 2;
            try {
                strArr[2] = "DISK_FULL";
                strArr[3] = "PIP_PACKAGE_UNAVAILABLE";
                strArr[4] = "SERVICE_FAILED";
                strArr[5] = "OUT_OF_MEMORY";
                int i2 = 6;
                strArr[6] = "NETWORK_TIMEOUT";
                strArr[7] = "PACKAGE_DOWNLOAD_FAILED";
                Set<String> setValues = values(strArr);
                if (jSONArrayOptJSONArray != null) {
                    for (int i3 = 0; i3 < Math.min(8, jSONArrayOptJSONArray.length()); i3++) {
                        if (setValues.contains(jSONArrayOptJSONArray.optString(i3))) {
                            jSONArray.put(jSONArrayOptJSONArray.optString(i3));
                        }
                    }
                }
                JSONObject jSONObject2 = new JSONObject();
                JSONObject jSONObjectOptJSONObject = jSONObject.optJSONObject("services");
                if (jSONObjectOptJSONObject != null) {
                    String[] strArr2 = {"replyai", "caddy"};
                    int i4 = 0;
                    while (i4 < i) {
                        String str3 = strArr2[i4];
                        int i5 = i;
                        JSONObject jSONObjectOptJSONObject2 = jSONObjectOptJSONObject.optJSONObject(str3);
                        if (jSONObjectOptJSONObject2 == null) {
                            str = str2;
                            c = c2;
                        } else {
                            c = c2;
                            JSONObject jSONObject3 = new JSONObject();
                            String strOptString = jSONObjectOptJSONObject2.optString("active");
                            str = str2;
                            String[] strArr3 = new String[i2];
                            strArr3[0] = "active";
                            strArr3[1] = "inactive";
                            strArr3[i5] = "failed";
                            strArr3[3] = "activating";
                            strArr3[4] = "deactivating";
                            strArr3[5] = str;
                            JSONObject jSONObjectPut2 = jSONObject3.put("active", choice(strOptString, values(strArr3)));
                            String strOptString2 = jSONObjectOptJSONObject2.optString("sub");
                            String[] strArr4 = new String[10];
                            strArr4[0] = "running";
                            strArr4[1] = "dead";
                            strArr4[i5] = "exited";
                            strArr4[3] = "failed";
                            strArr4[4] = "start";
                            strArr4[5] = "start-pre";
                            strArr4[i2] = "start-post";
                            strArr4[7] = "auto-restart";
                            strArr4[c] = "stop";
                            strArr4[9] = str;
                            JSONObject jSONObjectPut3 = jSONObjectPut2.put("sub", choice(strOptString2, values(strArr4)));
                            number(jSONObjectOptJSONObject2, jSONObjectPut3, "exit_status", 255L);
                            number(jSONObjectOptJSONObject2, jSONObjectPut3, "restarts", 2147483647L);
                            jSONObject2.put(str3, jSONObjectPut3);
                        }
                        i4++;
                        jSONObjectOptJSONObject = jSONObjectOptJSONObject;
                        i = i5;
                        c2 = c;
                        str2 = str;
                        i2 = 6;
                    }
                }
                return jSONObjectPut.put("reasons", jSONArray).put("services", jSONObject2);
            } catch (Exception unused) {
                return null;
            }
        } catch (Exception unused2) {
            return null;
        }
    }
}
