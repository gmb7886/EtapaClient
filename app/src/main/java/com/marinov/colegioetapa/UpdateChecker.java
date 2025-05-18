package com.marinov.colegioetapa;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;

public class UpdateChecker {
    private static final String TAG = "UpdateChecker";
    private static final String PREFS_NAME = "UpdatePrefs";
    private static final String KEY_LAST_VERSION = "last_version";

    public interface UpdateListener {
        void onUpdateAvailable(String url);
        void onUpToDate();
        void onError(String message);
    }

    public static void checkForUpdate(Context context, UpdateListener listener) {
        new Thread(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/gmb7886/EtapaClient/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "EtapaClient-Android");
                conn.setConnectTimeout(10000);

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    String json = readResponseStream(conn);
                    JSONObject release = new JSONObject(json);
                    String latestVersion = release.getString("tag_name");
                    String currentVersion = BuildConfig.VERSION_NAME;

                    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    String lastNotifiedVersion = prefs.getString(KEY_LAST_VERSION, "");

                    if (!latestVersion.equals(currentVersion) && !latestVersion.equals(lastNotifiedVersion)) {
                        prefs.edit().putString(KEY_LAST_VERSION, latestVersion).apply();
                        listener.onUpdateAvailable(release.getString("html_url"));
                    } else {
                        listener.onUpToDate();
                    }
                } else {
                    listener.onError("HTTP error: " + conn.getResponseCode());
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro na verificação", e);
                listener.onError(e.getMessage());
            }
        }).start();
    }

    private static String readResponseStream(HttpURLConnection conn) throws Exception {
        try (InputStream is = conn.getInputStream();
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            return br.lines().collect(Collectors.joining("\n"));
        }
    }
}