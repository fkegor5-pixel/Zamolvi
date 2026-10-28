package ru.replyai.android;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLException;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public final class RemoteClient {
    private static volatile RequestObserver observer;
    private final String base;
    private final ConnectionFactory factory;
    private final String token;

    public interface ConnectionFactory {
        HttpURLConnection open(URL url) throws Exception;
    }

    interface RequestObserver {
        void complete(String str, int i, long j, String str2, Throwable th);
    }

    static void observe(RequestObserver requestObserver) {
        observer = requestObserver;
    }

    public RemoteClient(String str, String str2) throws Exception {
        this(str, str2, new ConnectionFactory() { // from class: ru.replyai.android.RemoteClient$$ExternalSyntheticLambda0
            @Override // ru.replyai.android.RemoteClient.ConnectionFactory
            public final HttpURLConnection open(URL url) {
                return RemoteClient.lambda$new$0(url);
            }
        });
    }

    static /* synthetic */ HttpURLConnection lambda$new$0(URL url) throws Exception {
        return (HttpURLConnection) url.openConnection();
    }

    RemoteClient(String str, String str2, ConnectionFactory connectionFactory) throws Exception {
        this.base = normalizeUrl(str);
        validateToken(str2);
        this.token = str2;
        this.factory = connectionFactory;
    }

    public static String normalizeUrl(String str) throws Exception {
        try {
            URI uri = new URI(str.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/"))) {
                throw new Exception("Нужен HTTPS адрес сервера без пути, логина, ключа и параметров.");
            }
            String aSCIIString = uri.toASCIIString();
            return aSCIIString.endsWith("/") ? aSCIIString.substring(0, aSCIIString.length() - 1) : aSCIIString;
        } catch (Exception unused) {
            throw new Exception("Укажите адрес сервера: https://ваш-домен");
        }
    }

    public static void validateToken(String str) throws Exception {
        if (str.length() < 32 || str.length() > 256 || !str.matches("[!-~]+")) {
            throw new Exception("Ключ управления должен содержать от 32 до 256 символов без пробелов.");
        }
    }

    /* JADX WARN: Code duplicated, block: B:100:0x019d A[Catch: IOException -> 0x01b4, SSLException -> 0x01b6, all -> 0x01d9, Exception -> 0x01dc, TryCatch #16 {Exception -> 0x01dc, blocks: (B:79:0x0134, B:78:0x0131, B:69:0x0122, B:95:0x0165, B:97:0x0184, B:98:0x0195, B:100:0x019d, B:101:0x01ae, B:102:0x01b1, B:109:0x01b8, B:110:0x01d8), top: B:179:0x00f5 }] */
    /* JADX WARN: Code duplicated, block: B:167:0x025e  */
    /* JADX WARN: Code duplicated, block: B:177:0x012c A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:183:0x0250 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:186:0x010e A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:193:0x00d6 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:196:0x00ec A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:202:0x011e A[SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:203:0x0116 A[SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:206:? A[Catch: all -> 0x0135, IOException -> 0x01b4, SSLException -> 0x01b6, Exception -> 0x01dc, SYNTHETIC, TRY_LEAVE, TryCatch #16 {Exception -> 0x01dc, blocks: (B:79:0x0134, B:78:0x0131, B:69:0x0122, B:95:0x0165, B:97:0x0184, B:98:0x0195, B:100:0x019d, B:101:0x01ae, B:102:0x01b1, B:109:0x01b8, B:110:0x01d8), top: B:179:0x00f5 }] */
    /* JADX WARN: Code duplicated, block: B:208:? A[SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:55:0x00f7  */
    /* JADX WARN: Code duplicated, block: B:59:0x0102 A[Catch: all -> 0x0126, TRY_LEAVE, TryCatch #3 {all -> 0x0126, blocks: (B:56:0x00f9, B:57:0x00fb, B:59:0x0102), top: B:175:0x00f9 }] */
    /* JADX WARN: Code duplicated, block: B:69:0x0122 A[Catch: all -> 0x0135, IOException -> 0x01b4, SSLException -> 0x01b6, Exception -> 0x01dc, EDGE_INSN: B:69:0x0122->B:188:0x013a BREAK  A[LOOP:0: B:57:0x00fb->B:62:0x010e], TRY_ENTER, TRY_LEAVE, TryCatch #16 {Exception -> 0x01dc, blocks: (B:79:0x0134, B:78:0x0131, B:69:0x0122, B:95:0x0165, B:97:0x0184, B:98:0x0195, B:100:0x019d, B:101:0x01ae, B:102:0x01b1, B:109:0x01b8, B:110:0x01d8), top: B:179:0x00f5 }] */
    /* JADX WARN: Code duplicated, block: B:82:0x0138  */
    /* JADX WARN: Code duplicated, block: B:97:0x0184 A[Catch: IOException -> 0x01b4, SSLException -> 0x01b6, all -> 0x01d9, Exception -> 0x01dc, TryCatch #16 {Exception -> 0x01dc, blocks: (B:79:0x0134, B:78:0x0131, B:69:0x0122, B:95:0x0165, B:97:0x0184, B:98:0x0195, B:100:0x019d, B:101:0x01ae, B:102:0x01b1, B:109:0x01b8, B:110:0x01d8), top: B:179:0x00f5 }] */
    public JSONObject call(String str, String str2, JSONObject jSONObject) throws Exception {
        long j;
        String headerField;
        int responseCode;
        long j2;
        Throwable th;
        IOException iOException;
        SSLException sSLException;
        Throwable th2;
        RequestObserver requestObserver;
        Exception exc;
        InputStream errorStream;
        ByteArrayOutputStream byteArrayOutputStream;
        JSONObject jSONObject2;
        String strOptString;
        Throwable th3;
        byte[] bArr;
        int i;
        long j3;
        if (!str2.matches("/api/[a-z/]+")) {
            throw new Exception("Некорректный путь API.");
        }
        long jNanoTime = System.nanoTime();
        HttpURLConnection httpURLConnectionOpen = null;
        long j4 = 1000000;
        try {
            try {
                httpURLConnectionOpen = this.factory.open(new URL(this.base + str2));
                httpURLConnectionOpen.setInstanceFollowRedirects(false);
                int i2 = 15000;
                httpURLConnectionOpen.setConnectTimeout(15000);
                if (!str2.equals("/api/diagnostics")) {
                    i2 = 95000;
                }
                httpURLConnectionOpen.setReadTimeout(i2);
                httpURLConnectionOpen.setRequestMethod(str);
                httpURLConnectionOpen.setRequestProperty("Authorization", "Bearer " + this.token);
                httpURLConnectionOpen.setRequestProperty("Accept", "application/json");
                httpURLConnectionOpen.setUseCaches(false);
                if (jSONObject == null) {
                    j = jNanoTime;
                    responseCode = httpURLConnectionOpen.getResponseCode();
                    headerField = httpURLConnectionOpen.getHeaderField("X-ReplyAI-Request-ID");
                    errorStream = responseCode >= 400 ? httpURLConnectionOpen.getErrorStream() : httpURLConnectionOpen.getInputStream();
                    byteArrayOutputStream = new ByteArrayOutputStream();
                    if (errorStream != null) {
                        bArr = new byte[4096];
                        while (true) {
                            i = errorStream.read(bArr);
                            if (i != -1) {
                                j2 = j4;
                                if (errorStream != null) {
                                    break;
                                }
                                errorStream.close();
                                break;
                            }
                            j3 = j4;
                            if (byteArrayOutputStream.size() + i <= 1000000) {
                                throw new Exception("Сервер вернул слишком большой ответ.");
                            }
                            byteArrayOutputStream.write(bArr, 0, i);
                            j4 = j3;
                            th = th;
                            th3 = th;
                            if (errorStream != null) {
                                throw th3;
                            }
                            errorStream.close();
                            throw th3;
                        }
                    }
                    j2 = 1000000;
                    jSONObject2 = new JSONObject(byteArrayOutputStream.toString("UTF-8"));
                    if (responseCode >= 200) {
                    }
                    strOptString = jSONObject2.optString("detail", "Ошибка сервера: HTTP " + responseCode);
                    if (strOptString.contains(this.token)) {
                        strOptString = "Ошибка сервера: HTTP " + responseCode;
                    }
                    if (strOptString.length() > 300) {
                        strOptString = "Ошибка сервера: HTTP " + responseCode;
                    }
                    throw new Exception(strOptString);
                }
                try {
                    byte[] bytes = jSONObject.toString().getBytes(StandardCharsets.UTF_8);
                    httpURLConnectionOpen.setDoOutput(true);
                    httpURLConnectionOpen.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    httpURLConnectionOpen.setFixedLengthStreamingMode(bytes.length);
                    OutputStream outputStream = httpURLConnectionOpen.getOutputStream();
                    try {
                        outputStream.write(bytes);
                        if (outputStream != null) {
                            outputStream.close();
                        }
                        j = jNanoTime;
                        try {
                            responseCode = httpURLConnectionOpen.getResponseCode();
                            try {
                                headerField = httpURLConnectionOpen.getHeaderField("X-ReplyAI-Request-ID");
                                if (responseCode >= 400) {
                                    try {
                                    } catch (SSLException e) {
                                        sSLException = e;
                                        throw new Exception("Не удалось проверить HTTPS сертификат сервера. Проверьте домен и настройку TLS.", sSLException);
                                    } catch (IOException e2) {
                                        iOException = e2;
                                        throw new Exception("Сервер недоступен. Проверьте интернет, адрес и состояние VPS.", iOException);
                                    } catch (Exception e3) {
                                        exc = e3;
                                        throw exc;
                                    } catch (Throwable th4) {
                                        th = th4;
                                        j2 = 1000000;
                                        th2 = null;
                                        requestObserver = observer;
                                        if (requestObserver != null) {
                                            try {
                                                requestObserver.complete(str2, responseCode, (System.nanoTime() - j) / j2, headerField, th2);
                                            } catch (RuntimeException unused) {
                                            }
                                        }
                                        if (httpURLConnectionOpen != null) {
                                            throw th;
                                        }
                                        httpURLConnectionOpen.disconnect();
                                        throw th;
                                    }
                                } else {
                                    try {
                                    } catch (SSLException e4) {
                                        e = e4;
                                        sSLException = e;
                                        throw new Exception("Не удалось проверить HTTPS сертификат сервера. Проверьте домен и настройку TLS.", sSLException);
                                    } catch (IOException e5) {
                                        e = e5;
                                        iOException = e;
                                        throw new Exception("Сервер недоступен. Проверьте интернет, адрес и состояние VPS.", iOException);
                                    } catch (Exception e6) {
                                        e = e6;
                                        throw e;
                                    } catch (Throwable th5) {
                                        th = th5;
                                        j2 = 1000000;
                                        th = null;
                                        th2 = th;
                                        requestObserver = observer;
                                        if (requestObserver != null) {
                                            requestObserver.complete(str2, responseCode, (System.nanoTime() - j) / j2, headerField, th2);
                                        }
                                        if (httpURLConnectionOpen != null) {
                                            throw th;
                                        }
                                        httpURLConnectionOpen.disconnect();
                                        throw th;
                                    }
                                }
                                byteArrayOutputStream = new ByteArrayOutputStream();
                                try {
                                    try {
                                        if (errorStream != null) {
                                            try {
                                                try {
                                                    bArr = new byte[4096];
                                                    while (true) {
                                                        i = errorStream.read(bArr);
                                                        if (i != -1) {
                                                            j2 = j4;
                                                            if (errorStream != null) {
                                                                break;
                                                            }
                                                            errorStream.close();
                                                            break;
                                                        }
                                                        j3 = j4;
                                                        if (byteArrayOutputStream.size() + i <= 1000000) {
                                                            throw new Exception("Сервер вернул слишком большой ответ.");
                                                        }
                                                        try {
                                                            byteArrayOutputStream.write(bArr, 0, i);
                                                            j4 = j3;
                                                        } catch (Throwable th6) {
                                                            th = th6;
                                                        }
                                                        th = th6;
                                                        th3 = th;
                                                        if (errorStream != null) {
                                                            throw th3;
                                                        }
                                                        try {
                                                            errorStream.close();
                                                            throw th3;
                                                        } catch (Throwable th7) {
                                                            th3.addSuppressed(th7);
                                                            throw th3;
                                                        }
                                                    }
                                                } catch (Throwable th8) {
                                                    th = th8;
                                                }
                                            } catch (Throwable th9) {
                                                th = th9;
                                                th2 = null;
                                                requestObserver = observer;
                                                if (requestObserver != null) {
                                                    requestObserver.complete(str2, responseCode, (System.nanoTime() - j) / j2, headerField, th2);
                                                }
                                                if (httpURLConnectionOpen != null) {
                                                    throw th;
                                                }
                                                httpURLConnectionOpen.disconnect();
                                                throw th;
                                            }
                                        } else {
                                            j2 = 1000000;
                                        }
                                        try {
                                            try {
                                                jSONObject2 = new JSONObject(byteArrayOutputStream.toString("UTF-8"));
                                                if (responseCode >= 200 || responseCode >= 300) {
                                                    strOptString = jSONObject2.optString("detail", "Ошибка сервера: HTTP " + responseCode);
                                                    if (strOptString.contains(this.token)) {
                                                        strOptString = "Ошибка сервера: HTTP " + responseCode;
                                                    }
                                                    if (strOptString.length() > 300) {
                                                        strOptString = "Ошибка сервера: HTTP " + responseCode;
                                                    }
                                                    throw new Exception(strOptString);
                                                }
                                                RequestObserver requestObserver2 = observer;
                                                if (requestObserver2 != null) {
                                                    try {
                                                        requestObserver2.complete(str2, responseCode, (System.nanoTime() - j) / j2, headerField, null);
                                                    } catch (RuntimeException unused2) {
                                                    }
                                                }
                                                if (httpURLConnectionOpen != null) {
                                                    httpURLConnectionOpen.disconnect();
                                                }
                                                return jSONObject2;
                                            } catch (Throwable th10) {
                                                th = th10;
                                                th2 = th;
                                                requestObserver = observer;
                                                if (requestObserver != null) {
                                                    requestObserver.complete(str2, responseCode, (System.nanoTime() - j) / j2, headerField, th2);
                                                }
                                                if (httpURLConnectionOpen != null) {
                                                    throw th;
                                                }
                                                httpURLConnectionOpen.disconnect();
                                                throw th;
                                            }
                                        } catch (Exception unused3) {
                                            throw new Exception("Сервер не вернул JSON (HTTP " + responseCode + "). Проверьте адрес и HTTPS.");
                                        } catch (Throwable th11) {
                                            th = th11;
                                            th = null;
                                            th2 = th;
                                            requestObserver = observer;
                                            if (requestObserver != null) {
                                                requestObserver.complete(str2, responseCode, (System.nanoTime() - j) / j2, headerField, th2);
                                            }
                                            if (httpURLConnectionOpen != null) {
                                                throw th;
                                            }
                                            httpURLConnectionOpen.disconnect();
                                            throw th;
                                        }
                                    } catch (Exception e7) {
                                        e = e7;
                                        throw e;
                                    }
                                } catch (SSLException e8) {
                                    e = e8;
                                    sSLException = e;
                                    throw new Exception("Не удалось проверить HTTPS сертификат сервера. Проверьте домен и настройку TLS.", sSLException);
                                } catch (IOException e9) {
                                    e = e9;
                                    iOException = e;
                                    throw new Exception("Сервер недоступен. Проверьте интернет, адрес и состояние VPS.", iOException);
                                }
                            } catch (SSLException e10) {
                                sSLException = e10;
                            } catch (IOException e11) {
                                iOException = e11;
                            } catch (Exception e12) {
                                throw e12;
                            } catch (Throwable th12) {
                                th = th12;
                                j2 = 1000000;
                                th = null;
                                headerField = "";
                            }
                        } catch (SSLException e13) {
                            e = e13;
                            sSLException = e;
                            throw new Exception("Не удалось проверить HTTPS сертификат сервера. Проверьте домен и настройку TLS.", sSLException);
                        } catch (IOException e14) {
                            e = e14;
                            iOException = e;
                            throw new Exception("Сервер недоступен. Проверьте интернет, адрес и состояние VPS.", iOException);
                        } catch (Exception e15) {
                            e = e15;
                            throw e;
                        } catch (Throwable th13) {
                            th = th13;
                            j2 = 1000000;
                            th = null;
                            headerField = "";
                            responseCode = 0;
                            th2 = th;
                            requestObserver = observer;
                            if (requestObserver != null) {
                                requestObserver.complete(str2, responseCode, (System.nanoTime() - j) / j2, headerField, th2);
                            }
                            if (httpURLConnectionOpen != null) {
                                throw th;
                            }
                            httpURLConnectionOpen.disconnect();
                            throw th;
                        }
                    } catch (Throwable th14) {
                        if (outputStream == null) {
                            throw th14;
                        }
                        try {
                            outputStream.close();
                            throw th14;
                        } catch (Throwable th15) {
                            th14.addSuppressed(th15);
                            throw th14;
                        }
                    }
                } catch (SSLException e16) {
                    sSLException = e16;
                    throw new Exception("Не удалось проверить HTTPS сертификат сервера. Проверьте домен и настройку TLS.", sSLException);
                } catch (IOException e17) {
                    iOException = e17;
                    throw new Exception("Сервер недоступен. Проверьте интернет, адрес и состояние VPS.", iOException);
                } catch (Exception e18) {
                    exc = e18;
                    throw exc;
                } catch (Throwable th16) {
                    th = th16;
                    j = jNanoTime;
                    headerField = "";
                    responseCode = 0;
                    j2 = 1000000;
                    th2 = null;
                    requestObserver = observer;
                    if (requestObserver != null) {
                        requestObserver.complete(str2, responseCode, (System.nanoTime() - j) / j2, headerField, th2);
                    }
                    if (httpURLConnectionOpen != null) {
                        throw th;
                    }
                    httpURLConnectionOpen.disconnect();
                    throw th;
                }
            } catch (Throwable th17) {
                th = th17;
                headerField = "";
                responseCode = 0;
            }
        } catch (SSLException e19) {
            e = e19;
        } catch (IOException e20) {
            e = e20;
        } catch (Exception e21) {
            e = e21;
        } catch (Throwable th18) {
            th = th18;
            j = jNanoTime;
        }
        requestObserver = observer;
        if (requestObserver != null) {
            requestObserver.complete(str2, responseCode, (System.nanoTime() - j) / j2, headerField, th2);
        }
        if (httpURLConnectionOpen != null) {
            throw th;
        }
        httpURLConnectionOpen.disconnect();
        throw th;
    }
}
