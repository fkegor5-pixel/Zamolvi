package ru.replyai.android;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/**
 * «Вход в Arizona»: the owner logs in to arizona-rp.com here (the captcha is solved by the owner), the app takes the
 * login token the site itself uses and the server keeps it for «статистика». The password is never read.
 */
public final class ArizonaLoginActivity extends FeatureActivity {
    private static final String SITE = "https://arizona-rp.com/";
    private static final Pattern JWT = Pattern.compile("eyJ[\\w-]+\\.[\\w-]+\\.[\\w-]+");
    // the site's storage: only values under a key about a token, so analytics ids are not taken for the login
    private static final String SCAN = "(function(){var r=[];[localStorage,sessionStorage].forEach(function(s){"
            + "for(var i=0;i<s.length;i++){var k=s.key(i);if(/token|auth|access/i.test(k))r.push(s.getItem(k));}});"
            + "return r.join(' ');})()";

    private final Set<String> tried = new HashSet<>();
    private WebView web;
    private boolean sending, done;

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        title("Вход в Arizona");
        text("Войдите в свой аккаунт Arizona RP ниже и пройдите капчу. Как только сайт пустит вас, приложение само "
                + "возьмёт токен входа и сохранит его на вашем VPS для команды «статистика». Пароль приложение не читает.", 14);
        status = text("Ждём входа на сайт…", 15);
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true); // the captcha
        web.setOnTouchListener((v, e) -> { // the page scrolls itself inside the screen's scroll
            if (e.getAction() == MotionEvent.ACTION_DOWN) v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // the site's own requests to its backend carry «Authorization: Bearer …» once logged in
                for (Map.Entry<String, String> h : request.getRequestHeaders().entrySet()) {
                    if ("authorization".equalsIgnoreCase(h.getKey()) && h.getValue().startsWith("Bearer ")) {
                        String token = h.getValue().substring(7).trim();
                        ui.post(() -> offer(token));
                    }
                }
                return null;
            }

            @Override public void onPageFinished(WebView view, String url) { scan(); }
        });
        int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.72f);
        body.addView(web, new LinearLayout.LayoutParams(-1, height));
        web.loadUrl(SITE);
        ui.postDelayed(this::poll, 2000);
    }

    /** Every two seconds: the site's storage and cookies, for sites that keep the token there. */
    private void poll() {
        if (done || closed || isFinishing()) return;
        scan();
        ui.postDelayed(this::poll, 2000);
    }

    private void scan() {
        if (done || web == null) return;
        web.evaluateJavascript(SCAN, value -> offerAll(value));
        CookieManager cookies = CookieManager.getInstance();
        offerAll(cookies.getCookie(SITE));
        offerAll(cookies.getCookie("https://backend.arizona-rp.com/"));
    }

    private void offerAll(String text) {
        if (text == null) return;
        Matcher m = JWT.matcher(text);
        while (m.find()) offer(m.group());
    }

    /** A candidate goes to the server, which keeps it only if the site accepts it. */
    private void offer(String token) {
        if (done || sending || token.length() < 20 || !tried.add(token)) return;
        sending = true;
        status.setText("Проверяю вход…");
        worker.execute(() -> {
            String message;
            boolean ok = false;
            try {
                JSONObject data = new JSONObject().put("token", token);
                JSONObject answer = request("PUT", "/api/arizona", data);
                String name = answer.optString("name");
                String until = answer.optString("until");
                message = "✅ Готово" + (name.isEmpty() ? "" : ": " + name) + ". Пишите «статистика» в Telegram."
                        + (until.isEmpty() ? "" : "\nВход действует до " + until + ".");
                ok = true;
            } catch (Exception e) {
                String m = e.getMessage() == null ? "" : e.getMessage();
                message = oldServer(m) ? OLD_SERVER : "Ждём входа на сайт… (" + m + ")";
            }
            final String text = message;
            final boolean saved = ok;
            ui.post(() -> {
                if (closed || isFinishing()) return;
                sending = false;
                done = saved;
                status.setText(text);
            });
        });
    }

    @Override protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
