package com.marinov.colegioetapa;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

public class InscricaoAccFragment extends Fragment {
    public static final String URL = "https://acc.colegioetapa.com.br/";
    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_ASKED_STORAGE = "asked_storage";
    private static final int REQUEST_STORAGE_PERMISSION = 1001;

    private WebView webView;
    private LinearLayout layoutSemInternet;
    private MaterialButton btnTentarNovamente;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Inicializa elementos da UI
        layoutSemInternet = view.findViewById(R.id.layout_sem_internet);
        btnTentarNovamente = view.findViewById(R.id.btn_tentar_novamente);
        webView = view.findViewById(R.id.webview);
        // Verifica conexão inicial
        if (isOnline()) {
            initializeWebView(view);
        } else {
            showNoInternetUI();
        }

        return view;
    }
    private void navigateToHome() {
        try {
            BottomNavigationView bottomNav = requireActivity().findViewById(R.id.bottom_navigation);
            bottomNav.setSelectedItemId(R.id.navigation_home);
        } catch (Exception e) {
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "WrongConstant"})
    private void initializeWebView(View view) {
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setVisibility(View.INVISIBLE);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        applyWebViewDarkMode(settings);

        // Configurações de segurança
        webView.setOnLongClickListener(v -> true);
        webView.setLongClickable(false);
        webView.setHapticFeedbackEnabled(false);

        restoreCookies();
        checkStoragePermissions();
        webView.loadUrl(URL);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                saveCookies();
                removeHeader(view);
                if (isSystemDarkMode()) {
                    injectCssDarkMode(view);
                }
                showWebViewWithAnimation(view);
                layoutSemInternet.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (!isOnline()) {
                    showNoInternetUI();
                }
            }
        });

        configureDownloadListener();
    }

    private void applyWebViewDarkMode(WebSettings settings) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            if (isSystemDarkMode()) {
                WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_ON);
                if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                    WebSettingsCompat.setForceDarkStrategy(
                            settings,
                            WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY
                    );
                }
            } else {
                WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF);
            }
        }
    }

    private boolean isSystemDarkMode() {
        int uiMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private void removeHeader(WebView view) {
        String js = "document.documentElement.style.webkitTouchCallout='none';" +
                "document.documentElement.style.webkitUserSelect='none';" +
                "var nav = document.querySelector('#page-content-wrapper > nav');" +
                "if(nav) nav.parentNode.removeChild(nav);" +
                "var sidebar = document.querySelector('#sidebar-wrapper');" +
                "if(sidebar) sidebar.parentNode.removeChild(sidebar);" +
                "var style = document.createElement('style');" +
                "style.type = 'text/css';" +
                "style.appendChild(document.createTextNode('::-webkit-scrollbar{display:none;}'));" +
                "document.head.appendChild(style);";
        view.evaluateJavascript(js, null);
    }

    private void injectCssDarkMode(WebView view) {
        String css = "html {filter: invert(1) hue-rotate(180deg) !important; background: #121212 !important;}" +
                "img, picture, video, iframe {filter: invert(1) hue-rotate(180deg) !important;}";
        String js = "(function() {" +
                "var style = document.createElement('style');" +
                "style.type = 'text/css';" +
                "style.textContent = \"" + css + "\";" +
                "document.head.appendChild(style);" +
                "})();";
        view.evaluateJavascript(js, null);
    }

    private void showWebViewWithAnimation(WebView view) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            view.setAlpha(0f);
            view.setVisibility(View.VISIBLE);
            view.animate().alpha(1f).setDuration(300).start();
        }, 100);
    }

    private void showNoInternetUI() {
        webView.setVisibility(View.GONE);
        layoutSemInternet.setVisibility(View.VISIBLE);

        btnTentarNovamente.setOnClickListener(v -> navigateToHome());{
            if (isOnline()) {
                layoutSemInternet.setVisibility(View.GONE);
                webView.reload();
            }
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return (netInfo != null && netInfo.isConnected());
    }

    // Métodos de cookies (implemente conforme sua lógica)
    private void restoreCookies() {/* Sua implementação */}
    private void saveCookies() {/* Sua implementação */}

    private void checkStoragePermissions() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && !prefs.getBoolean(KEY_ASKED_STORAGE, false)) {
            prefs.edit().putBoolean(KEY_ASKED_STORAGE, true).apply();
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            }
        }
    }

    private void configureDownloadListener() {
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (needsStoragePermission() && !hasStoragePermission()) {
                Toast.makeText(requireContext(), "Permissão de armazenamento necessária", Toast.LENGTH_SHORT).show();
                return;
            }
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.allowScanningByMediaScanner();
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType));
            DownloadManager dm = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) dm.enqueue(request);
        });
    }

    private boolean needsStoragePermission() {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q;
    }

    private boolean hasStoragePermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (webView != null) {
            applyWebViewDarkMode(webView.getSettings());
            webView.reload();
        }
    }

    @Override
    public void onDestroyView() {
        if (webView != null) {
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}