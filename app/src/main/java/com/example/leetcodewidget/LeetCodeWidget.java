package com.example.leetcodewidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LeetCodeWidget extends AppWidgetProvider {

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.leet_code_widget);

        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        new Thread(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences("leetcode_widget", Context.MODE_PRIVATE);
                String username = prefs.getString("username", "shekharrrr");

                OkHttpClient client = new OkHttpClient();

                // Combined query: submissionCalendar (full history) + recentAcSubmissionList (dedup recent days)
                String graphqlQuery =
                        "{\"query\":\"query($username:String!){" +
                                "recentAcSubmissionList(username:$username,limit:20){titleSlug timestamp}" +
                                " matchedUser(username:$username){userCalendar{submissionCalendar}}" +
                                "}\",\"variables\":{\"username\":\"" + username + "\"}}";

                RequestBody body = RequestBody.create(
                        MediaType.parse("application/json"), graphqlQuery
                );

                Request request = new Request.Builder()
                        .url("https://leetcode.com/graphql")
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("User-Agent", "Mozilla/5.0")
                        .build();

                Response response = client.newCall(request).execute();
                String result = response.body().string();

                JSONObject obj = new JSONObject(result);
                JSONObject data = obj.getJSONObject("data");

                // --- Step 1: Parse submissionCalendar as base (covers full history) ---
                // userCalendar.submissionCalendar only counts accepted submissions
                JSONObject userCalendar = data.getJSONObject("matchedUser").getJSONObject("userCalendar");
                String calendarString = userCalendar.getString("submissionCalendar");
                JSONObject submissionCalendar = new JSONObject(calendarString);

                // Build base count map from submissionCalendar
                // Keys in submissionCalendar are already UTC day-aligned timestamps
                Map<Long, Integer> baseCountMap = new HashMap<>();
                Iterator<String> keys = submissionCalendar.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    long ts = Long.parseLong(k);
                    int count = submissionCalendar.getInt(k);
                    baseCountMap.put(ts, count);
                }

                // --- Step 2: Build unique-problem map from recentAcSubmissionList ---
                // This gives us accurate deduplication for recent days
                JSONArray submissions = data.getJSONArray("recentAcSubmissionList");

                Map<Long, Set<String>> recentUniqueSlugs = new HashMap<>();
                for (int j = 0; j < submissions.length(); j++) {
                    JSONObject sub = submissions.getJSONObject(j);
                    long ts = sub.getLong("timestamp");
                    String slug = sub.getString("titleSlug");

                    long dayKey = (ts / 86400) * 86400;

                    if (!recentUniqueSlugs.containsKey(dayKey)) {
                        recentUniqueSlugs.put(dayKey, new HashSet<>());
                    }
                    recentUniqueSlugs.get(dayKey).add(slug);
                }

                // --- Step 3: Compute todayKey using local calendar date ---
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, 12);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long todayKey = (cal.getTimeInMillis() / 1000L / 86400) * 86400;

                // --- Step 4: Render 30-day heatmap ---
                for (int i = 0; i < 30; i++) {
                    long key = todayKey - ((29 - i) * 86400L);

                    int count;
                    if (recentUniqueSlugs.containsKey(key)) {
                        // Recent day: use accurate unique-problem count
                        count = recentUniqueSlugs.get(key).size();
                    } else {
                        // Older day: fall back to submissionCalendar (accepted submissions count)
                        count = baseCountMap.containsKey(key) ? baseCountMap.get(key) : 0;
                    }

                    int drawable;
                    if (count == 0)
                        drawable = R.drawable.heatmap_empty;
                    else if (count == 1)
                        drawable = R.drawable.heatmap_light;
                    else if (count <= 3)
                        drawable = R.drawable.heatmap_medium;
                    else
                        drawable = R.drawable.heatmap_dark;

                    int viewId = context.getResources()
                            .getIdentifier("day" + (i + 1), "id", context.getPackageName());

                    if (viewId != 0) {
                        views.setInt(viewId, "setBackgroundResource", drawable);
                        views.setOnClickPendingIntent(viewId, pendingIntent);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            appWidgetManager.updateAppWidget(appWidgetId, views);

        }).start();
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                          int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        updateAppWidget(context, appWidgetManager, appWidgetId);
    }
}
