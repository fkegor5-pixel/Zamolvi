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
            public final HttpURLConnection open(URL url) throws Exception {
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

    /**
     * One JSON request to the server API. Rewritten by hand from the 2.7 bytecode: same path check,
     * timeouts, 1 MB answer limit, error texts and observer report as the original.
     */
    public JSONObject call(String method, String path, JSONObject body) throws Exception {
        if (!path.matches("/api/[a-z/]+")) {
            throw new Exception("Некорректный путь API.");
        }
        long started = System.nanoTime();
        HttpURLConnection connection = null;
        int code = 0;
        String requestId = null;
        Throwable failure = null;
        try {
            connection = factory.open(new URL(base + path));
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(path.equals("/api/diagnostics") ? 15000 : 95000);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Accept", "application/json");
            connection.setUseCaches(false);
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(bytes);
                }
            }
            code = connection.getResponseCode();
            requestId = connection.getHeaderField("X-ReplyAI-Request-ID");
            ByteArrayOutputStream answer = new ByteArrayOutputStream();
            try (InputStream in = code >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                if (in != null) {
                    byte[] buffer = new byte[4096];
                    for (int n; (n = in.read(buffer)) != -1; ) {
                        if (answer.size() + n > 1000000) throw new Exception("Сервер вернул слишком большой ответ.");
                        answer.write(buffer, 0, n);
                    }
                }
            }
            JSONObject json;
            try {
                json = new JSONObject(answer.toString("UTF-8"));
            } catch (Exception notJson) {
                throw new Exception("Сервер не вернул JSON (HTTP " + code + "). Проверьте адрес и HTTPS.");
            }
            if (code < 200 || code >= 300) {
                String detail = json.optString("detail", "Ошибка сервера: HTTP " + code);
                if (detail.contains(token) || detail.length() > 300) detail = "Ошибка сервера: HTTP " + code;
                throw new Exception(detail);
            }
            return json;
        } catch (SSLException error) {
            failure = error;
            throw new Exception("Не удалось проверить HTTPS сертификат сервера. Проверьте домен и настройку TLS.", error);
        } catch (IOException error) {
            failure = error;
            throw new Exception("Сервер недоступен. Проверьте интернет, адрес и состояние VPS.", error);
        } catch (Exception error) {
            failure = error;
            throw error;
        } finally {
            RequestObserver watcher = observer;
            if (watcher != null) {
                try {
                    watcher.complete(path, code, (System.nanoTime() - started) / 1000000, requestId, failure);
                } catch (RuntimeException ignored) {
                }
            }
            if (connection != null) connection.disconnect();
        }
    }
}
