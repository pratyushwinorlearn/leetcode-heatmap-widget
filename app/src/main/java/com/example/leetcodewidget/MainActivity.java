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

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
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

        // Schedule periodic WorkManager updates on first launch
        scheduleWidgetUpdates();

        // Prompt user to disable battery optimization (important for background updates)
        requestBatteryOptimizationExemption();

        saveButton.setOnClickListener(v -> {
            String username = usernameInput.getText().toString().trim();

            if (!username.isEmpty()) {
                prefs.edit().putString("username", username).apply();

                // Immediately refresh widget with new username
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
                WidgetUpdateWorker.class,
                30, TimeUnit.MINUTES
        )
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

                String graphqlQuery =
                        "{\"query\":\"query($username:String!){matchedUser(username:$username){submissionCalendar}}\",\"variables\":{\"username\":\""
                                + username + "\"}}";

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

                runOnUiThread(() -> {
                    try {
                        java.util.TimeZone tz = java.util.TimeZone.getDefault();
                        long now = System.currentTimeMillis();
                        long today = (now + tz.getOffset(now)) / 1000;

                        for (int i = 0; i < 30; i++) {
                            long dayTimestamp = today - (i * 86400L);
                            long key = (dayTimestamp / 86400) * 86400;

                            int count = calendar.optInt(String.valueOf(key), 0);

                            Date date = new Date(key * 1000);
                            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
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