package ru.replyai.android;

import android.app.Application;
import android.os.Process;
public final class ReplyAiApplication extends Application {
    @Override // android.app.Application
    public void onCreate() {
        super.onCreate();
        DiagnosticLog.initialize(this);
        Thread.UncaughtExceptionHandler defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        if (!(defaultUncaughtExceptionHandler instanceof CrashRecorder)) {
            Thread.setDefaultUncaughtExceptionHandler(new CrashRecorder(defaultUncaughtExceptionHandler));
        }
        DiagnosticLog.event("app", "started", null);
    }

    private static final class CrashRecorder implements Thread.UncaughtExceptionHandler {
        private final Thread.UncaughtExceptionHandler previous;

        CrashRecorder(Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {
            this.previous = uncaughtExceptionHandler;
        }

        @Override // java.lang.Thread.UncaughtExceptionHandler
        public void uncaughtException(Thread thread, Throwable th) {
            try {
                DiagnosticLog.event("crash", "uncaught", th);
            } catch (Throwable unused) {
            }
            Thread.UncaughtExceptionHandler uncaughtExceptionHandler = this.previous;
            if (uncaughtExceptionHandler != null) {
                uncaughtExceptionHandler.uncaughtException(thread, th);
            } else {
                Process.killProcess(Process.myPid());
                System.exit(10);
            }
        }
    }
}
