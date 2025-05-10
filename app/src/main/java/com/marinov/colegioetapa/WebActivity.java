package com.marinov.colegioetapa;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Objects;

public class WebActivity extends AppCompatActivity {
    private WebView webView;
    public static final String EXTRA_URL = "extra_url";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web);

        // Ajuste de insets para status/navigation bar
        View root = findViewById(R.id.web_activity_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom);
            return insets;
        });

        webView = findViewById(R.id.webview);

        // Configurações do WebView
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        // --- BLOQUEIO DE ZOOM ---
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // --- BLOQUEIO DE SELEÇÃO DE TEXTO ---
        webView.setOnLongClickListener(v -> true);
        webView.setLongClickable(false);
        webView.setHapticFeedbackEnabled(false);

        // WebViewClient com injeção de JS para garantir bloqueio de seleção
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // injeta CSS/JS para desativar seleção e menu de contexto
                view.evaluateJavascript(
                        "document.documentElement.style.webkitUserSelect='none';" +
                                "document.documentElement.style.webkitTouchCallout='none';",
                        null
                );
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                view.loadData(
                        "<h3>Erro ao carregar página</h3>",
                        "text/html",
                        "utf-8"
                );
            }
        });

        // Carrega a URL passada ou a padrão
        String url = getIntent().getStringExtra(EXTRA_URL);
        webView.loadUrl(Objects.requireNonNullElse(url, HomeFragment.URL));
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
