package com.marinov.colegioetapa;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.color.MaterialColors;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        setupToolbar();
        setupUI();
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

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            btnCheck.setEnabled(false);
            btnCheck.setAlpha(0.5f);
            Toast.makeText(
                    this,
                    "Função de atualização disponível a partir do Android 7.0",
                    Toast.LENGTH_LONG
            ).show();
        } else {
            btnCheck.setOnClickListener(v -> checkUpdate());
        }

        btnClear.setOnClickListener(v -> {
            CookieManager cm = CookieManager.getInstance();
            cm.removeAllCookies(success -> {});
            cm.flush();
            Toast.makeText(this, "Base de dados apagada com sucesso!", Toast.LENGTH_SHORT).show();
        });
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
                    String json = readResponseStream(conn);
                    JSONObject release = new JSONObject(json);
                    processReleaseData(release);
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
        String current = BuildConfig.VERSION_NAME;

        if (latest.equals(current)) {
            showMessage();
        } else {
            String releaseUrl = release.getString("html_url");
            promptForUpdate(releaseUrl);
        }
    }

    private void promptForUpdate(String url) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle("Atualização Disponível")
                .setMessage("Deseja atualizar?")
                .setPositiveButton("Sim", (dialog, which) -> showWebView(url))
                .setNegativeButton("Não", null)
                .show());
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showWebView(String url) {
        WebView webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        webView.setWebViewClient(new WebViewClient());
        webView.setDownloadListener((downloadUrl, userAgent, contentDisposition, mimeType, contentLength) -> {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            String fileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(request);

            Toast.makeText(this, "Download iniciado, isso pode demorar um pouco: " + fileName, Toast.LENGTH_SHORT).show();
        });

        new AlertDialog.Builder(this)
                .setView(webView)
                .setNegativeButton("Fechar", (d, w) -> d.dismiss())
                .show();

        webView.loadUrl(url);
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