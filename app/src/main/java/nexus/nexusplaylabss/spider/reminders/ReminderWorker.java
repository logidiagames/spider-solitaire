package com.nexusplaylabss.spider.reminders;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Runs at the planned reminder time, shows what is due and plans the next run. */
public final class ReminderWorker extends Worker {
    public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull @Override public Result doWork() {
        Reminders.fire(getApplicationContext());
        return Result.success();
    }
}
