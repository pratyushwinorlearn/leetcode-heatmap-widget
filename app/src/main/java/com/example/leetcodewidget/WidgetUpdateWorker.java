package com.example.leetcodewidget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class WidgetUpdateWorker extends Worker {

    public WidgetUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, LeetCodeWidget.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);

        for (int id : appWidgetIds) {
            LeetCodeWidget.updateAppWidget(context, appWidgetManager, id);
        }

        return Result.success();
    }
}