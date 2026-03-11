/*
 * LeetCode Heatmap Widget
 * Author: Shekhar Pratyush
 * Year: 2026
 * Description: Android widget displaying LeetCode activity heatmap.
 */
package com.example.leetcodewidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RemoteViews;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LeetCodeWidget extends AppWidgetProvider {


    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.leet_code_widget);

        // Make widget clickable → open MainActivity
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // When any square is clicked, open the app
        views.setOnClickPendingIntent(R.id.day1, pendingIntent);

        new Thread(() -> {
            try {

                // Read saved username
                SharedPreferences prefs = context.getSharedPreferences("leetcode_widget", Context.MODE_PRIVATE);
                String username = prefs.getString("username", "shekharrrr");

                OkHttpClient client = new OkHttpClient();

                String graphqlQuery =
                        "{\"query\":\"query($username:String!){matchedUser(username:$username){submissionCalendar}}\",\"variables\":{\"username\":\""
                                + username +
                                "\"}}";

                RequestBody body = RequestBody.create(
                        MediaType.parse("application/json"),
                        graphqlQuery
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
                JSONObject matchedUser = data.getJSONObject("matchedUser");

                String calendarString = matchedUser.getString("submissionCalendar");
                JSONObject calendar = new JSONObject(calendarString);

                long today = System.currentTimeMillis() / 1000;

                for (int i = 0; i < 30; i++) {

                    long dayTimestamp = today - (i * 86400);
                    long key = (dayTimestamp / 86400) * 86400;

                    int count = calendar.optInt(String.valueOf(key), 0);

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

                    views.setInt(viewId, "setBackgroundResource", drawable);

                    // Make every square clickable
                    views.setOnClickPendingIntent(viewId, pendingIntent);
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
    public void onAppWidgetOptionsChanged(
            Context context,
            AppWidgetManager appWidgetManager,
            int appWidgetId,
            Bundle newOptions) {

        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);

        updateAppWidget(context, appWidgetManager, appWidgetId);
    }


}
