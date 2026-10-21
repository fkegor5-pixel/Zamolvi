package ru.replyai.android;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Runs the version check about once a day while the phone has a network. */
public final class UpdateJob extends JobService {
    private static final int ID = 0x2A1F;
    private static final long PERIOD_MS = 24 * 60 * 60 * 1000L;

    /** Asks the system for a daily job; an already scheduled one is kept as it is. */
    static void schedule(Context c) {
        try {
            JobScheduler scheduler = c.getSystemService(JobScheduler.class);
            if (scheduler == null || scheduler.getPendingJob(ID) != null) return;
            scheduler.schedule(new JobInfo.Builder(ID, new ComponentName(c, UpdateJob.class))
                    .setPeriodic(PERIOD_MS)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(false) // no BOOT permission needed: it is scheduled again on every start
                    .build());
        } catch (RuntimeException error) {
            // A vendor shell may refuse to schedule jobs; the button on the screen still works.
        }
    }

    @Override public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            Updates.checkInBackground(getApplicationContext());
            jobFinished(params, false);
        }).start();
        return true; // the work continues on its own thread
    }

    @Override public boolean onStopJob(JobParameters params) {
        return true; // try again on the next period
    }
}
