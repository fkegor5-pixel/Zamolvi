package ru.replyai.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import androidx.core.view.ViewCompat;
import org.json.JSONArray;

/* JADX INFO: loaded from: classes.dex */
public final class QrCodeView extends View {
    private final Paint paint;
    private String[] rows;

    public QrCodeView(Context context) {
        super(context);
        this.paint = new Paint();
        setContentDescription("QR входа в Telegram на вашем VPS");
    }

    void setRows(JSONArray jSONArray) {
        if (jSONArray == null) {
            this.rows = null;
            invalidate();
            return;
        }
        int length = jSONArray.length();
        if (length < 25 || length > 129) {
            throw new IllegalArgumentException("Invalid QR matrix");
        }
        String[] strArr = new String[length];
        for (int i = 0; i < length; i++) {
            String strOptString = jSONArray.optString(i);
            if (strOptString.length() != length || !strOptString.matches("[01]+")) {
                throw new IllegalArgumentException("Invalid QR row");
            }
            strArr[i] = strOptString;
        }
        this.rows = strArr;
        invalidate();
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        Canvas canvas2;
        super.onDraw(canvas);
        canvas.drawColor(-1);
        if (this.rows == null) {
            return;
        }
        int iMax = Math.max(1, Math.min(getWidth(), getHeight()) / this.rows.length);
        int width = (getWidth() - (this.rows.length * iMax)) / 2;
        int height = (getHeight() - (this.rows.length * iMax)) / 2;
        this.paint.setColor(ViewCompat.MEASURED_STATE_MASK);
        this.paint.setAntiAlias(false);
        for (int i = 0; i < this.rows.length; i++) {
            int i2 = 0;
            while (true) {
                String[] strArr = this.rows;
                if (i2 < strArr.length) {
                    if (strArr[i].charAt(i2) == '1') {
                        canvas2 = canvas;
                        canvas2.drawRect((i2 * iMax) + width, (i * iMax) + height, ((i2 + 1) * iMax) + width, ((i + 1) * iMax) + height, this.paint);
                    } else {
                        canvas2 = canvas;
                    }
                    i2++;
                    canvas = canvas2;
                }
            }
        }
    }
}
