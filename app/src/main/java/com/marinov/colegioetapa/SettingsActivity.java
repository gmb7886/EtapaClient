package com.marinov.colegioetapa;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.color.MaterialColors;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";
    private BroadcastReceiver downloadReceiver;

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
                insetsController.setAppearanceLightStatusBars(!isDarkMode());
            }
        } else {
            int statusBarColor = MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorSurface,
                    Color.BLACK
            );
            getWindow().setStatusBarColor(statusBarColor);
            if (!isDarkMode() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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

        // Desabilita verificação em Android ≤ 6.0
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

        // Limpar dados: remove todos os cookies do WebView
        btnClear.setOnClickListener(v -> {
            CookieManager cm = CookieManager.getInstance();
            cm.removeAllCookies(success -> { /* opcional */ });
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
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Consulta GitHub Releases para nova versão */
    private void checkUpdate() {
        new Thread(() -> {
            try {
                URL url = new URL(
                        "https://api.github.com/repos/gmb7886/EtapaClient/releases/latest"
                );
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "EtapaClient-Android");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);

                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_FORBIDDEN) {
                    showError("Limite de requisições excedido. Tente mais tarde.");
                    return;
                } else if (code != HttpURLConnection.HTTP_OK) {
                    showError("Erro na conexão: Código " + code);
                    return;
                }

                String json = readResponseStream(conn);
                JSONObject release = new JSONObject(json);
                processReleaseData(release);

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
            return;
        }

        JSONArray assets = release.getJSONArray("assets");
        String downloadUrl = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String name = asset.getString("name");
            if (name.startsWith("app") && name.endsWith(".apk")) {
                downloadUrl = asset.getString("browser_download_url");
                break;
            }
        }

        if (downloadUrl == null) {
            showError("APK não encontrado no release");
        } else {
            promptForUpdate(downloadUrl);
        }
    }

    private void promptForUpdate(String url) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle("Nova versão disponível")
                .setMessage("Deseja atualizar agora?")
                .setPositiveButton("Atualizar", (d, w) -> downloadAndInstall(url))
                .setNegativeButton("Cancelar", null)
                .show()
        );
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void downloadAndInstall(String url) {
        try {
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            File dest = new File(
                    getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    "etapa_update.apk"
            );
            req.setDestinationUri(Uri.fromFile(dest))
                    .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    );
            long id = dm.enqueue(req);

            // Receiver para instalar ao concluir download
            downloadReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    long rid = intent.getLongExtra(
                            DownloadManager.EXTRA_DOWNLOAD_ID, -1
                    );
                    if (rid == id) {
                        installApk(dest);
                        unregisterReceiver(this);
                    }
                }
            };
            registerReceiver(
                    downloadReceiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            );
        } catch (Exception e) {
            Log.e(TAG, "Erro no download", e);
            showError("Falha no download: " + e.getMessage());
        }
    }

    private void installApk(File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    this,
                    BuildConfig.APPLICATION_ID + ".provider",
                    apk
            );
            Intent i = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            i.setData(uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Log.e(TAG, "Erro na instalação", e);
            showError("Erro na instalação: " + e.getMessage());
        }
    }

    private void showMessage() {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setMessage("Você já está na versão mais recente")
                .setPositiveButton("OK", null)
                .show()
        );
    }

    private void showError(String msg) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle("Erro")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show()
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (downloadReceiver != null) {
            try {
                unregisterReceiver(downloadReceiver);
            } catch (IllegalArgumentException ignored) { }
        }
    }
}
