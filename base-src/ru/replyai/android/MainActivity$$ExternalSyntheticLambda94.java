package ru.replyai.android;

import org.json.JSONObject;
public final /* synthetic */ class MainActivity$$ExternalSyntheticLambda94 implements MainActivity.Result {
    public final /* synthetic */ MainActivity f$0;

    public /* synthetic */ MainActivity$$ExternalSyntheticLambda94(MainActivity mainActivity) {
        this.f$0 = mainActivity;
    }

    @Override // ru.replyai.android.MainActivity.Result
    public final void accept(JSONObject jSONObject) {
        this.f$0.loginNext(jSONObject);
    }
}
