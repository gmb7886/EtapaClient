package com.marinov.colegioetapa;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

public class HomeFragment extends Fragment {
    private static final int REQUEST_STORAGE_PERMISSION = 100;
    private static final String PREFS_PERMISSIONS = "app_permissions";
    private static final String KEY_ASKED_STORAGE = "asked_storage_permission";
    private static final String COOKIE_PREFS = "cookies";
    private static final String SAVED_COOKIES_KEY = "saved_cookies";
    public static final String URL = "https://areaexclusiva.colegioetapa.com.br/home";

    private WebView webView;
    private SharedPreferences cookiePrefs;
    private CookieManager cookieManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true); // Mantém a instância do fragmento
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @SuppressLint({"SetJavaScriptEnabled", "WrongConstant"})
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Configuração inicial do WebView
        if (webView == null) {
            initializeWebView(view);
        } else {
            reattachWebView(view);
        }

        // Configuração de insets dinâmica
        configureSystemBarsInsets(view);
    }

    private void initializeWebView(View view) {
        webView = view.findViewById(R.id.webview);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // --- BLOQUEIO DE ZOOM ---
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // --- BLOQUEIO DE SELEÇÃO DE TEXTO ---
        webView.setOnLongClickListener(v -> true);
        webView.setLongClickable(false);
        webView.setHapticFeedbackEnabled(false);

        // Configuração de cookies
        cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // Restaurar cookies salvos
        restoreCookies();

        // Verificação de permissões
        checkStoragePermissions();

        // Configuração do WebViewClient
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Log.d("WebView", "Carregando: " + url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                saveCookies();
                // Injeta CSS/JS para garantir que não haja seleção de texto
                view.evaluateJavascript(
                        "document.documentElement.style.webkitTouchCallout='none';" +
                                "document.documentElement.style.webkitUserSelect='none';",
                        null
                );
                Log.d("WebView", "Página carregada: " + url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                handleWebViewError(error);
            }
        });

        // Configuração de downloads
        configureDownloadListener();

        // Carregar URL se houver conexão
        if (hasInternetConnection()) {
            webView.loadUrl(URL);
        } else {
            showNoConnectionToast();
        }
    }

    private void reattachWebView(View view) {
        ViewGroup parent = (ViewGroup) webView.getParent();
        if (parent != null) parent.removeView(webView);

        ConstraintLayout container = view.findViewById(R.id.container);
        container.addView(webView);
    }

    private void configureSystemBarsInsets(View view) {
        webView = view.findViewById(R.id.webview);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (webView != null) {
                webView.setPadding(
                        insets.left,
                        insets.top,
                        insets.right,
                        insets.bottom
                );
            }
            return windowInsets;
        });
    }

    private void restoreCookies() {
        cookiePrefs = requireActivity().getSharedPreferences(COOKIE_PREFS, Context.MODE_PRIVATE);
        String savedCookies = cookiePrefs.getString(SAVED_COOKIES_KEY, "");
        if (!savedCookies.isEmpty()) {
            for (String cookie : savedCookies.split(";")) {
                cookieManager.setCookie(URL, cookie.trim());
            }
            cookieManager.flush();
        }
    }

    private void saveCookies() {
        String cookies = cookieManager.getCookie(URL);
        cookiePrefs.edit().putString(SAVED_COOKIES_KEY, cookies).apply();
    }

    private void checkStoragePermissions() {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_PERMISSIONS, Context.MODE_PRIVATE);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && !prefs.getBoolean(KEY_ASKED_STORAGE, false)) {
            prefs.edit().putBoolean(KEY_ASKED_STORAGE, true).apply();
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            }
        }
    }

    private void configureDownloadListener() {
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (needsStoragePermission() && !hasStoragePermission()) {
                showStoragePermissionToast();
                return;
            }
            DownloadManager.Request request = createDownloadRequest(url, userAgent, contentDisposition, mimeType);
            executeDownload(request);
        });
    }

    private boolean needsStoragePermission() {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q;
    }

    private boolean hasStoragePermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private DownloadManager.Request createDownloadRequest(String url, String userAgent,
                                                          String contentDisposition, String mimeType) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        return request.setMimeType(mimeType)
                .addRequestHeader("User-Agent", userAgent)
                .setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        URLUtil.guessFileName(url, contentDisposition, mimeType));
    }

    private void executeDownload(DownloadManager.Request request) {
        try {
            DownloadManager dm = (DownloadManager) requireActivity().getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(request);
                showDownloadStartedToast();
            }
        } catch (Exception e) {
            handleDownloadError(e);
        }
    }

    private boolean hasInternetConnection() {
        ConnectivityManager cm = (ConnectivityManager) requireContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null &&
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }

    private void handleWebViewError(WebResourceError error) {
        String errorMsg = "Erro: " + error.getErrorCode() + " - " + error.getDescription();
        Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show();
        Log.e("WebView", errorMsg);
    }

    private void showNoConnectionToast() {
        Toast.makeText(requireContext(), "Sem conexão com a internet!", Toast.LENGTH_LONG).show();
    }

    private void showStoragePermissionToast() {
        Toast.makeText(requireContext(), "Permissão de armazenamento necessária!", Toast.LENGTH_LONG).show();
    }

    private void showDownloadStartedToast() {
        Toast.makeText(requireContext(), "Download iniciado...", Toast.LENGTH_SHORT).show();
    }

    private void handleDownloadError(Exception e) {
        Toast.makeText(requireContext(), "Erro ao iniciar download!", Toast.LENGTH_LONG).show();
        Log.e("Download", "Erro: ", e);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "Permissão concedida!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Permissão necessária para downloads!", Toast.LENGTH_LONG).show();
            }
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
