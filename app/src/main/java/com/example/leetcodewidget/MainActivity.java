package com.example.leetcodewidget;

import androidx.appcompat.app.AppCompatActivity;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    LinearLayout container;
    EditText usernameInput;
    Button saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        container = findViewById(R.id.activityContainer);
        usernameInput = findViewById(R.id.usernameInput);
        saveButton = findViewById(R.id.saveButton);

        SharedPreferences prefs = getSharedPreferences("leetcode_widget", MODE_PRIVATE);
        String savedUsername = prefs.getString("username", "shekharrrr");

        usernameInput.setText(savedUsername);

        scheduleWidgetUpdates();
        requestBatteryOptimizationExemption();

        saveButton.setOnClickListener(v -> {
            String username = usernameInput.getText().toString().trim();
            if (!username.isEmpty()) {
                prefs.edit().putString("username", username).apply();

                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
                ComponentName componentName = new ComponentName(this, LeetCodeWidget.class);
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
                for (int id : appWidgetIds) {
                    LeetCodeWidget.updateAppWidget(this, appWidgetManager, id);
                }

                container.removeAllViews();
                loadActivity(username);
            }
        });

        loadActivity(savedUsername);
    }

    private void scheduleWidgetUpdates() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                WidgetUpdateWorker.class, 30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "leetcode_widget_update",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
        );
    }

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void loadActivity(String username) {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();

                // Combined query: full history from submissionCalendar + recent dedup from recentAcSubmissionList
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

                // --- Step 1: submissionCalendar as base (full year history, accepted only) ---
                JSONObject userCalendar = data.getJSONObject("matchedUser").getJSONObject("userCalendar");
                String calendarString = userCalendar.getString("submissionCalendar");
                JSONObject submissionCalendar = new JSONObject(calendarString);

                Map<Long, Integer> baseCountMap = new HashMap<>();
                Iterator<String> keys = submissionCalendar.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    long ts = Long.parseLong(k);
                    int count = submissionCalendar.getInt(k);
                    baseCountMap.put(ts, count);
                }

                // --- Step 2: recentAcSubmissionList for accurate dedup of recent days ---
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

                // --- Step 3: todayKey from local calendar date ---
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, 12);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long todayKey = (cal.getTimeInMillis() / 1000L / 86400) * 86400;

                runOnUiThread(() -> {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
                        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

                        for (int i = 0; i < 30; i++) {
                            long key = todayKey - (i * 86400L);

                            int count;
                            if (recentUniqueSlugs.containsKey(key)) {
                                // Recent: accurate unique problem count
                                count = recentUniqueSlugs.get(key).size();
                            } else {
                                // Older: fall back to submissionCalendar
                                count = baseCountMap.containsKey(key) ? baseCountMap.get(key) : 0;
                            }

                            Date date = new Date(key * 1000L);
                            String text = sdf.format(date) + "  →  " + count + " problems";

                            TextView tv = new TextView(MainActivity.this);
                            tv.setText(text);
                            tv.setTextSize(18);
                            tv.setPadding(0, 15, 0, 15);
                            container.addView(tv);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
