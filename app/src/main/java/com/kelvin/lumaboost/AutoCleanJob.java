package com.kelvin.lumaboost;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Limpeza diária enquanto o aparelho carrega: cache excedente, cache do Luma e temporários. */
public final class AutoCleanJob extends JobService {
    private static final int JOB_ID = 4201;

    static void schedule(Context context, boolean enabled) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (!enabled) {
            scheduler.cancel(JOB_ID);
            return;
        }
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, AutoCleanJob.class))
                .setPeriodic(AppCatalog.DAY_MS)
                .setRequiresCharging(true)
                .setPersisted(true)
                .build();
        scheduler.schedule(job);
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Context context = getApplicationContext();
                long before = DeviceStats.freeStorage();
                CacheCleaner.trimSystemCache(context);
                CacheCleaner.cleanOwnCaches(context);
                if (Permissions.hasAllFilesAccess(context)) {
                    JunkScanner.quickClean();
                }
                long freed = Math.max(0L, DeviceStats.freeStorage() - before);
                Prefs.addFreed(context, freed, 0L);
                Prefs.setLastAutoSummary(context, Format.bytes(freed) + " liberados em "
                        + android.text.format.DateFormat.getDateFormat(context).format(new java.util.Date()));
                jobFinished(params, false);
            }
        }, "luma-autoclean").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
