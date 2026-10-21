package ru.replyai.android;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import org.json.JSONObject;

/** «Фото и документы»: OCR and image analysis, PDF/DOCX/TXT reading. */
public final class MediaActivity extends FeatureActivity {
    private Switch photos;
    private Switch documents;
    private EditText model;
    private EditText maxMb;
    private EditText pdfPages;
    private EditText scanPages;
    private android.widget.TextView modelNote;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        title("Фото и документы");
        text("Фото: AI переписывает текст с изображения (OCR) и коротко описывает, что на нём, а автоответчик "
                + "отвечает с учётом этого. Документы: PDF, DOCX, TXT, CSV и JSON читаются на сервере; страницы-сканы "
                + "PDF распознаются как фото.", 15);
        text("Файл скачивается в память VPS, превращается в текст и сразу удаляется. Распознавание изображения — "
                + "отдельный запрос к AI: на платном тарифе это расход, он виден в «Подробной статистике». Для фото "
                + "нужна модель, которая принимает изображения. Подпись к файлу обрабатывается как обычный текст.", 14);
        photos = toggle("Распознавать фото и изображения");
        documents = toggle("Читать документы");
        LinearLayout box = section();
        heading(box, "Модель для изображений");
        modelNote = label(box, "", 13, true);
        model = field(box, "Модель по умолчанию", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        heading(box, "Ограничения");
        label(box, "Максимальный размер файла, МБ (1–20)", 13, true);
        maxMb = field(box, "10", "", InputType.TYPE_CLASS_NUMBER);
        label(box, "Сколько страниц PDF читать (1–50)", 13, true);
        pdfPages = field(box, "20", "", InputType.TYPE_CLASS_NUMBER);
        label(box, "Сколько страниц-сканов распознавать (0–5)", 13, true);
        scanPages = field(box, "3", "", InputType.TYPE_CLASS_NUMBER);
        status = text("", 15);
        button("Сохранить", v -> save());
        execute(() -> request("GET", "/api/media", null), this::render);
    }

    private Switch toggle(String label) {
        Switch view = new Switch(this);
        view.setText(label);
        view.setTextColor(colors.ink);
        view.setPadding(0, dp(12), 0, dp(12));
        body.addView(view);
        controls.add(view);
        return view;
    }

    private void save() {
        int mb, pages, scans;
        try {
            mb = Integer.parseInt(maxMb.getText().toString().trim());
            pages = Integer.parseInt(pdfPages.getText().toString().trim());
            scans = Integer.parseInt(scanPages.getText().toString().trim());
        } catch (NumberFormatException e) {
            status.setText("Заполните ограничения числами.");
            return;
        }
        String vision = model.getText().toString().trim();
        execute(() -> request("PUT", "/api/media", new JSONObject()
                .put("photos", photos.isChecked()).put("documents", documents.isChecked())
                .put("vision_model", vision).put("max_mb", mb).put("pdf_pages", pages).put("scan_pages", scans)), value -> {
            render(value);
            status.setText("Сохранено.");
        });
    }

    private void render(JSONObject value) throws Exception {
        photos.setChecked(value.getBoolean("photos"));
        documents.setChecked(value.getBoolean("documents"));
        model.setText(value.optString("vision_model"));
        model.setHint("По умолчанию: " + value.optString("effective_vision_model"));
        maxMb.setText(String.valueOf(value.getInt("max_mb")));
        pdfPages.setText(String.valueOf(value.getInt("pdf_pages")));
        scanPages.setText(String.valueOf(value.getInt("scan_pages")));
        String provider = value.optString("provider");
        modelNote.setText("groq".equals(provider)
                ? "Для Groq нужна модель с поддержкой изображений: основная модель ответов картинки не понимает. Оставьте поле пустым, чтобы использовать модель по умолчанию."
                : "gigachat".equals(provider)
                ? "Для GigaChat нужна модель с поддержкой изображений, например GigaChat-2-Max или GigaChat-2-Pro."
                : "gemini".equals(provider)
                ? "Модели Gemini понимают изображения: пустое поле — использовать модель ответов."
                : "Своё API (OpenRouter и др.): нужна модель, принимающая изображения, иначе будет HTTP 404. "
                        + "На openrouter.ai/models включите фильтр Input → Image.");
        status.setText(value.getBoolean("photos") || value.getBoolean("documents") ? "Обработка включена." : "Обработка выключена.");
    }
}
