package com.marinov.colegioetapa;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.google.android.material.color.MaterialColors;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";
    private static final int DOWNLOAD_NOTIFICATION_ID = 1000;
    private static final int INSTALL_NOTIFICATION_ID = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        setupToolbar();
        setupUI();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && getIntent().getBooleanExtra("open_update_directly", false)) {
            checkUpdate();
        }
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        configureStatusBar();
        setupToolbarInsets();
    }

    private void configureStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            WindowInsetsControllerCompat insetsController =
                    ViewCompat.getWindowInsetsController(getWindow().getDecorView());
            if (insetsController != null) {
                insetsController.setAppearanceLightStatusBars(isDarkMode());
            }
        } else {
            int statusBarColor = MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorSurface,
                    Color.BLACK
            );
            getWindow().setStatusBarColor(statusBarColor);
            if (isDarkMode() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getWindow().getDecorView()
                        .setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }
    }

    private void setupToolbarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.toolbar),
                (v, insets) -> {
                    int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                    v.setPadding(
                            v.getPaddingLeft(),
                            statusBarHeight,
                            v.getPaddingRight(),
                            v.getPaddingBottom()
                    );
                    return insets;
                }
        );
    }

    private void setupUI() {
        Button btnCheck = findViewById(R.id.btn_check_update);
        Button btnClear = findViewById(R.id.btn_clear_data);
        Button btnTwitter = findViewById(R.id.btn_twitter);
        Button btnReddit = findViewById(R.id.btn_reddit);
        Button btnGithub = findViewById(R.id.btn_github);
        Button btnYoutube = findViewById(R.id.btn_youtube);

        btnTwitter.setOnClickListener(v -> openUrl("http://x.com/gmb7886"));
        btnReddit.setOnClickListener(v -> openUrl("https://www.reddit.com/user/GMB7886/"));
        btnGithub.setOnClickListener(v -> openUrl("https://github.com/gmb7886/"));
        btnYoutube.setOnClickListener(v -> openUrl("https://youtube.com/@CanalDoMarinov"));

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            btnCheck.setEnabled(false);
            btnCheck.setAlpha(0.5f);
            Toast.makeText(
                    this,
                    "Função de atualização OTA disponível a partir do Android 12.0 ou superior",
                    Toast.LENGTH_LONG
            ).show();
        } else {
            btnCheck.setOnClickListener(v -> checkUpdate());
        }

        btnClear.setOnClickListener(v -> {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            clearAllCacheData();
            Toast.makeText(this, "Base de dados apagada com sucesso!", Toast.LENGTH_SHORT).show();
        });
    }

    private void clearAllCacheData() {
        clearSharedPreferences("horarios_prefs");
        clearSharedPreferences("calendario_prefs");
        clearSharedPreferences("materia_cache");
        clearSharedPreferences("notas_prefs");

        // Limpar downloads antigos
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File[] files = downloadsDir.listFiles((dir, name) -> name.endsWith(".apk"));
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
    }

    private void clearSharedPreferences(String name) {
        getSharedPreferences(name, MODE_PRIVATE).edit().clear().apply();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Log.e(TAG, "Erro ao abrir URL", e);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private boolean isDarkMode() {
        return (getResources().getConfiguration().uiMode &
                Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
    }

    private void checkUpdate() {
        new Thread(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/gmb7886/EtapaClient/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "EtapaClient-Android");
                conn.setConnectTimeout(10000);

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    processReleaseData(new JSONObject(readResponseStream(conn)));
                } else {
                    showError("Erro na conexão: Código " + conn.getResponseCode());
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro na verificação", e);
                showError("Erro: " + e.getMessage());
            }
        }).start();
    }

    private String readResponseStream(HttpURLConnection conn) throws Exception {
        try (InputStream is = conn.getInputStream();
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            return br.lines().collect(Collectors.joining("\n"));
        }
    }

    private void processReleaseData(JSONObject release) throws Exception {
        String latest = release.getString("tag_name");
        if (latest.equals(BuildConfig.VERSION_NAME)) {
            showMessage();
        } else {
            promptForUpdate(release.getString("html_url"));
        }
    }

    private void promptForUpdate(String url) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle("Atualização Disponível")
                .setMessage("Deseja atualizar?")
                .setPositiveButton("Sim", (d, w) -> showWebView(url))
                .setNegativeButton("Não", null)
                .show());
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showWebView(String url) {
        View content = getLayoutInflater().inflate(R.layout.dialog_webview, null, false);
        WebView webView = content.findViewById(R.id.dialog_webview);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.endsWith(".apk")) {
                    startManualDownload(url);
                    return true;
                }
                return super.shouldOverrideUrlLoading(view, url);
            }
        });

        new AlertDialog.Builder(this)
                .setView(content)
                .setNegativeButton("Fechar", (dialog, which) -> dialog.dismiss())
                .show();

        webView.loadUrl(url);
    }

    private void startManualDownload(String apkUrl) {
        new DownloadTask().execute(apkUrl);
    }

    @SuppressLint("StaticFieldLeak")
    private class DownloadTask extends AsyncTask<String, Integer, File> {
        private NotificationManager notificationManager;
        private NotificationCompat.Builder builder;

        @Override
        protected void onPreExecute() {
            checkNotificationPermission();
            notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            builder = new NotificationCompat.Builder(SettingsActivity.this, "update_channel")
                    .setContentTitle("Baixando atualização")
                    .setSmallIcon(R.drawable.ic_download)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setOngoing(true)
                    .setAutoCancel(false);
            createNotificationChannel();
            notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, builder.build());
        }

        @Override
        protected File doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                File outputFile = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        URLUtil.guessFileName(urls[0], null, null)
                );

                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(outputFile)) {

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long total = 0;
                    long fileLength = connection.getContentLength();

                    while ((bytesRead = input.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                        total += bytesRead;
                        if (fileLength > 0) {
                            publishProgress((int) (total * 100 / fileLength));
                        }
                    }
                }
                return outputFile;
            } catch (Exception e) {
                Log.e(TAG, "Download error", e);
                return null;
            }
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            builder.setProgress(100, progress[0], false)
                    .setContentText(progress[0] + "% concluído");
            notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, builder.build());
        }

        @Override
        protected void onPostExecute(File apkFile) {
            notificationManager.cancel(DOWNLOAD_NOTIFICATION_ID);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (apkFile != null && apkFile.exists()) {
                    showInstallNotification(apkFile);
                } else {
                    showError("Falha no download");
                }
            }, 1000);
        }

        private void createNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        "update_channel",
                        "Atualizações",
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Notificações de atualização do app");
                channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
                notificationManager.createNotificationChannel(channel);
            }
        }

        private void checkNotificationPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        SettingsActivity.this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        100
                );
            }
        }
    }

    private void showInstallNotification(File apkFile) {
        try {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

            Uri apkUri = FileProvider.getUriForFile(
                    this,
                    BuildConfig.APPLICATION_ID + ".provider",
                    apkFile
            );

            Intent installIntent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(apkUri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    installIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "update_channel")
                    .setContentTitle("Atualização pronta")
                    .setContentText("Toque para instalar")
                    .setSmallIcon(R.drawable.ic_download_complete)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL);

            notificationManager.notify(INSTALL_NOTIFICATION_ID, builder.build());
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Erro na notificação de instalação", e);
            showError("Erro na instalação: " + e.getMessage());
        }
    }

    private void showMessage() {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setMessage("Você já está na versão mais recente")
                .setPositiveButton("OK", null)
                .show());
    }

    private void showError(String msg) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle("Erro")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show());
    }
}