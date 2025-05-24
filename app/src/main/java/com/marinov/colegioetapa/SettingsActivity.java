package com.marinov.colegioetapa;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;
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
import java.io.FileOutputStream;
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

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            btnCheck.setEnabled(false);
            btnCheck.setAlpha(0.5f);
            Toast.makeText(
                    this,
                    "Função de atualização OTA disponível a partir do Android 7.0 ou superior",
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
            JSONArray assets = release.getJSONArray("assets");
            String apkUrl = null;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                if (asset.getString("name").endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url");
                    break;
                }
            }
            if (apkUrl != null) {
                promptForUpdate(apkUrl);
            } else {
                showError("Arquivo APK não encontrado no release.");
            }
        }
    }

    private void promptForUpdate(String url) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle("Atualização Disponível")
                .setMessage("Deseja baixar e instalar a versão mais recente?")
                .setPositiveButton("Sim", (d, w) -> startManualDownload(url))
                .setNegativeButton("Não", null)
                .show());
    }

    private void startManualDownload(String apkUrl) {
        new DownloadTask().execute(apkUrl);
    }

    @SuppressLint("StaticFieldLeak")
    private class DownloadTask extends AsyncTask<String, Integer, File> {
        private AlertDialog progressDialog;
        private ProgressBar progressBar;

        @Override
        protected void onPreExecute() {
            AlertDialog.Builder builder = new AlertDialog.Builder(SettingsActivity.this);
            View view = getLayoutInflater().inflate(R.layout.dialog_download_progress, null);
            progressBar = view.findViewById(R.id.progress_bar);
            builder.setView(view).setCancelable(false);
            progressDialog = builder.create();
            progressDialog.show();
        }

        @Override
        protected File doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                File outputFile = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "app_release.apk"
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
                Log.e(TAG, "Erro no download", e);
                return null;
            }
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            progressBar.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(File apkFile) {
            progressDialog.dismiss();
            if (apkFile != null && apkFile.exists()) {
                showInstallDialog(apkFile);
            } else {
                showError("Falha ao baixar o arquivo.");
            }
        }
    }

    private void showInstallDialog(File apkFile) {
        try {
            Uri apkUri = FileProvider.getUriForFile(
                    this,
                    BuildConfig.APPLICATION_ID + ".provider",
                    apkFile
            );

            Intent installIntent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(apkUri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            new AlertDialog.Builder(this)
                    .setTitle("Download concluído")
                    .setMessage("Deseja instalar a atualização agora?")
                    .setPositiveButton("Instalar", (d, w) -> startActivity(installIntent))
                    .setNegativeButton("Cancelar", null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Erro na instalação", e);
            showError("Erro ao iniciar a instalação: " + e.getMessage());
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