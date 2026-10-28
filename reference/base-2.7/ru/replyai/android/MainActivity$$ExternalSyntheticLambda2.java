package ru.replyai.android;

import org.json.JSONObject;

/* JADX INFO: compiled from: D8$$SyntheticClass */
/* JADX INFO: loaded from: classes.dex */
public final /* synthetic */ class MainActivity$$ExternalSyntheticLambda2 implements MainActivity.Result {
    public final /* synthetic */ MainActivity f$0;

    public /* synthetic */ MainActivity$$ExternalSyntheticLambda2(MainActivity mainActivity) {
        this.f$0 = mainActivity;
    }

    @Override // ru.replyai.android.MainActivity.Result
    public final void accept(JSONObject jSONObject) {
        this.f$0.showStatus(jSONObject);
    }
}
