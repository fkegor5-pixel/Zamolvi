package ru.replyai.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;
public final class StatsChartView extends View {
    private final ThemeManager.Palette colors;
    private JSONArray days;
    private final Paint paint;

    public StatsChartView(Context context) {
        super(context);
        this.paint = new Paint(1);
        this.days = new JSONArray();
        this.colors = ThemeManager.colors(context);
        setMinimumHeight(Math.round(dp(200.0f)));
    }

    private float dp(float f) {
        return f * getResources().getDisplayMetrics().density;
    }

    public void setDays(JSONArray jSONArray) {
        if (jSONArray == null) {
            jSONArray = new JSONArray();
        }
        this.days = jSONArray;
        StringBuilder sb = new StringBuilder("Ответы по дням: ");
        for (int i = 0; i < Math.min(7, this.days.length()); i++) {
            JSONObject jSONObjectOptJSONObject = this.days.optJSONObject(i);
            if (jSONObjectOptJSONObject != null) {
                sb.append(jSONObjectOptJSONObject.optString("date")).append(": ").append(jSONObjectOptJSONObject.optInt("replies")).append("; ");
            }
        }
        setContentDescription(sb.toString());
        invalidate();
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        int i;
        char c;
        super.onDraw(canvas);
        char c2 = 7;
        int iMin = Math.min(7, this.days.length());
        this.paint.setTextSize(dp(11.0f));
        this.paint.setTextAlign(Paint.Align.CENTER);
        if (iMin == 0) {
            this.paint.setColor(this.colors.muted);
            canvas.drawText("Статистика ещё не загружена", getWidth() / 2.0f, getHeight() / 2.0f, this.paint);
            return;
        }
        float height = getHeight() - dp(26.0f);
        float fDp = dp(28.0f);
        float width = getWidth() / iMin;
        int i2 = 0;
        int iMax = 1;
        for (int i3 = 0; i3 < iMin; i3++) {
            JSONObject jSONObjectOptJSONObject = this.days.optJSONObject(i3);
            if (jSONObjectOptJSONObject != null) {
                iMax = Math.max(iMax, jSONObjectOptJSONObject.optInt("replies"));
            }
        }
        int i4 = 0;
        while (i4 < iMin) {
            JSONObject jSONObjectOptJSONObject2 = this.days.optJSONObject(i4);
            if (jSONObjectOptJSONObject2 == null) {
                i = i4;
                c = c2;
            } else {
                int iMax2 = Math.max(i2, jSONObjectOptJSONObject2.optInt("replies"));
                float f = (i4 + 0.5f) * width;
                float f2 = (float) (((double) ((height - fDp) * iMax2)) / ((double) iMax));
                Paint paint = this.paint;
                ThemeManager.Palette palette = this.colors;
                paint.setColor(iMax2 == 0 ? palette.border : palette.accent);
                float f3 = 0.28f * width;
                i = i4;
                canvas.drawRoundRect(f - f3, height - Math.max(dp(3.0f), f2), f3 + f, height, dp(4.0f), dp(4.0f), this.paint);
                this.paint.setColor(this.colors.ink);
                canvas.drawText(String.valueOf(iMax2), f, (height - f2) - dp(8.0f), this.paint);
                String strOptString = jSONObjectOptJSONObject2.optString("date");
                if (strOptString.length() == 10) {
                    c = 7;
                    strOptString = strOptString.substring(8) + "." + strOptString.substring(5, 7);
                } else {
                    c = 7;
                }
                this.paint.setColor(this.colors.muted);
                canvas.drawText(strOptString, f, getHeight() - dp(6.0f), this.paint);
            }
            i4 = i + 1;
            c2 = c;
            iMin = iMin;
            i2 = 0;
        }
    }
}
