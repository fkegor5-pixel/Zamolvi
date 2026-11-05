package ru.replyai.android;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** «Серийчик»: days in a row both people wrote in a private chat, a picture with the number, pinned in the chat. */
public final class StreakActivity extends FeatureActivity {
    private static final int PICK = 41;
    private static final int SIDE = 1024; // the sample photo is sent at most this big: enough for the AI, small to upload

    private Switch enabled;
    private EditText prompt;
    private EditText model;
    private TextView modelNote;
    private TextView photoNote;
    private ImageView preview;
    private Button remove;
    private LinearLayout list;
    private final ArrayList<View> owned = new ArrayList<>();
    private String photo;       // null — keep the one on the server, "" — remove it, base64 — a new sample
    private boolean stored;     // the server has a sample photo

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        title("Серийчик");
        text("Серия дней, когда вы с собеседником пишете друг другу. Кто-то из вас пишет в личный чат «серийчик», "
                + "оба отвечают «да», и бот присылает картинку с числом дней в правом верхнем углу и закрепляет её. "
                + "Каждый следующий день, когда напишете вы оба, бот сообщит, что серийчик продлён.", 15);
        text("День идёт в счёт, если в нём написали оба: это считается по истории переписки. Картинку рисует нейросеть "
                + "из настроек AI, это платный запрос. Если нейросеть рисовать не умеет, придёт простая картинка с числом.", 14);
        enabled = toggle(body, "Включить серийчик", false);

        LinearLayout box = section();
        heading(box, "Что на картинке");
        prompt = field(box, "Например: два котёнка пьют какао у окна, мультяшный стиль", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        prompt.setMinLines(3);
        heading(box, "Фото-образец");
        photoNote = label(box, "Необязательно. Нейросеть изменит это фото по описанию выше.", 13, true);
        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setMaxHeight(dp(220));
        preview.setVisibility(View.GONE);
        box.addView(preview, new LinearLayout.LayoutParams(-2, -2));
        action(box, "Выбрать фото", v -> startActivityForResult(
                new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), PICK), owned);
        remove = action(box, "Убрать фото", v -> {
            photo = "";
            preview.setVisibility(View.GONE);
            showPhoto();
        }, owned);
        heading(box, "Модель для картинок");
        modelNote = label(box, "", 13, true);
        model = field(box, "Модель по умолчанию", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        list = section();
        status = text("", 15);
        button("Сохранить", v -> save());
        execute(() -> request("GET", "/api/streak", null), this::render);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        execute(() -> new JSONObject().put("photo", encode(uri)), value -> {
            photo = value.getString("photo");
            byte[] bytes = Base64.decode(photo, Base64.NO_WRAP);
            preview.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
            preview.setVisibility(View.VISIBLE);
            showPhoto();
            status.setText("Фото выбрано. Нажмите «Сохранить».");
        });
    }

    /** The picked photo as base64 JPEG: upright (EXIF) and at most SIDE pixels on the long side. */
    private String encode(Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, bounds); }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new Exception("Не удалось открыть фото.");
        BitmapFactory.Options options = new BitmapFactory.Options();
        while (Math.max(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2) >= SIDE) options.inSampleSize *= 2;
        Bitmap bitmap;
        try (InputStream in = getContentResolver().openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(in, null, options); }
        if (bitmap == null) throw new Exception("Не удалось открыть фото.");
        int rotation = 0;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            int o = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            rotation = o == ExifInterface.ORIENTATION_ROTATE_90 ? 90 : o == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                    : o == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
        } catch (Exception ignored) { } // no EXIF (PNG and others): as it is
        float scale = Math.min(1f, (float) SIDE / Math.max(bitmap.getWidth(), bitmap.getHeight()));
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        matrix.postRotate(rotation);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    private void showPhoto() {
        boolean has = photo == null ? stored : !photo.isEmpty();
        remove.setVisibility(has ? View.VISIBLE : View.GONE);
        photoNote.setText(has && preview.getVisibility() != View.VISIBLE
                ? "Фото-образец сохранено на сервере. Нейросеть изменит его по описанию выше."
                : "Необязательно. Нейросеть изменит это фото по описанию выше.");
    }

    private void save() {
        JSONObject data = new JSONObject();
        try {
            data.put("enabled", enabled.isChecked()).put("prompt", prompt.getText().toString().trim())
                    .put("model", model.getText().toString().trim());
            if (photo != null) data.put("photo", photo);
        } catch (Exception e) {
            showError(e);
            return;
        }
        execute(() -> request("PUT", "/api/streak", data), value -> {
            photo = null;
            render(value);
            status.setText("Сохранено.");
        });
    }

    private void render(JSONObject value) throws Exception {
        enabled.setChecked(value.getBoolean("enabled"));
        prompt.setText(value.optString("prompt"));
        model.setText(value.optString("model"));
        String fallback = value.optString("effective_model");
        model.setHint(fallback.isEmpty() ? "Модель по умолчанию" : "По умолчанию: " + fallback);
        String provider = value.optString("provider");
        modelNote.setText("custom".equals(provider)
                ? "Своё API (OpenRouter): нужна модель, которая рисует изображения. На openrouter.ai/models включите фильтр Output → Image."
                : "gemini".equals(provider)
                ? "Gemini: нужна модель, которая рисует изображения."
                : "Groq и GigaChat картинки не рисуют: придёт простая картинка с числом. Чтобы рисовала нейросеть, "
                        + "выберите в настройках AI Gemini или своё API (OpenRouter).");
        stored = value.optBoolean("photo");
        showPhoto();
        list.removeAllViews();
        heading(list, "Серийчики сейчас");
        JSONArray streaks = value.optJSONArray("streaks");
        if (streaks == null || streaks.length() == 0) {
            label(list, "Пока нет. Напишите «серийчик» в личный чат с собеседником.", 14, true);
        }
        for (int i = 0; streaks != null && i < streaks.length(); i++) {
            JSONObject s = streaks.getJSONObject(i);
            int days = s.getInt("days");
            label(list, s.optString("name") + " · " + days + " " + days(days), 15, false);
        }
        status.setText(value.getBoolean("enabled") ? "Серийчик включён." : "Серийчик выключен.");
    }

    private static String days(int n) {
        if (n % 10 == 1 && n % 100 != 11) return "день";
        return n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 12 || n % 100 > 14) ? "дня" : "дней";
    }
}
