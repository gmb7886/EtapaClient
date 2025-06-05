package com.marinov.colegioetapa;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public class WebViewFragment extends Fragment {
    private static final String ARG_URL = "url";
    private static final String HOME_PATH = "https://areaexclusiva.colegioetapa.com.br/home";
    private static final String PREFS_NAME = "app_prefs";
    private static final String AUTOFILL_PREFS = "autofill_prefs";
    private static final String KEY_ASKED_STORAGE = "asked_storage";
    private static final int REQUEST_STORAGE_PERMISSION = 1001;
    private static final String CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID = 1;

    private WebView webView;
    private LinearLayout layoutError;
    private LinearLayout layoutSemInternet;
    private MaterialButton btnTentarNovamente;
    private OnBackPressedCallback onBackPressedCallback;
    private SharedPreferences sharedPrefs;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        sharedPrefs = requireContext().getSharedPreferences(AUTOFILL_PREFS, Context.MODE_PRIVATE);
        createNotificationChannel();
    }

    @SuppressLint("MissingPermission")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_webview, container, false);

        layoutError = view.findViewById(R.id.layout_sem_internet);
        webView = view.findViewById(R.id.webview);
        layoutSemInternet = view.findViewById(R.id.layout_sem_internet);
        btnTentarNovamente = view.findViewById(R.id.btn_tentar_novamente);

        if (!isOnline()) {
            showNoInternetUI();
        } else {
            initializeWebView();
        }

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        onBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    requireActivity().getSupportFragmentManager().popBackStack();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), onBackPressedCallback);
    }

    static class JsInterface {
        private final SharedPreferences sharedPrefs;

        public JsInterface(SharedPreferences sharedPrefs) {
            this.sharedPrefs = sharedPrefs;
        }

        @JavascriptInterface
        public void saveCredentials(String user, String password) {
            sharedPrefs.edit()
                    .putString("user", user)
                    .putString("password", password)
                    .apply();
        }

        @JavascriptInterface
        public String getSavedUser() {
            return sharedPrefs.getString("user", "");
        }

        @JavascriptInterface
        public String getSavedPassword() {
            return sharedPrefs.getString("password", "");
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Downloads");
            NotificationManager mgr = requireContext().getSystemService(NotificationManager.class);
            if (mgr != null) mgr.createNotificationChannel(channel);
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    @SuppressLint("SetJavaScriptEnabled")
    private void initializeWebView() {
        // Configuração persistente de cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);
        cookieManager.flush();

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setVisibility(View.INVISIBLE);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.addJavascriptInterface(new JsInterface(sharedPrefs), "AndroidAutofill");
        applyWebViewDarkMode(settings);
        setupWebViewSecurity();
        checkStoragePermissions();

        String initialUrl = getArguments() != null ? getArguments().getString(ARG_URL) : null;
        if (initialUrl != null) {
            webView.loadUrl(initialUrl);
        }

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                removeHeader(view);
                injectAutoFillScript(view);

                String jsCheck = "(function(){" +
                        "var a = document.querySelector(\"#home_banners_carousel > div > div.carousel-item.active > a > img\");" +
                        "var b = document.querySelector(\"#page-content-wrapper > div.d-lg-flex > div.container-fluid.p-3 > div.card.bg-transparent.border-0 > div:nth-child(4) > div.col-12.col-lg-8.mb-5 > div > div.d-flex.flex-column.h-100.mb-2 > div.card.border-radius-card.mb-3.border-blue\");" +
                        "return (a!==null && b!==null).toString();" +
                        "})()";
                view.evaluateJavascript(jsCheck, value -> {
                    if ("true".equals(value.replace("\"", "")) && url.startsWith(HOME_PATH)) {
                        closeHomeFragment();
                        requireActivity().getSupportFragmentManager().popBackStack();
                    }
                });

                if (isSystemDarkMode()) injectCssDarkMode(view);
                showWebViewWithAnimation(view);
                layoutError.setVisibility(View.GONE);
                layoutSemInternet.setVisibility(View.GONE);
            }

            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (!isOnline()) showNoInternetUI();
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                injectAutoFillScript(view);
            }
        });

        configureDownloadListener();
    }

    private void injectAutoFillScript(WebView view) {
        String script = "(function() {" +
                "const observerConfig = { childList: true, subtree: true };" +
                "const userFields = ['#matricula'];" +
                "const passFields = ['#senha'];" +

                "function setupAutofill() {" +
                "   const userField = document.querySelector(userFields.join(', '));" +
                "   const passField = document.querySelector(passFields.join(', '));" +

                "   if (userField && passField) {" +
                "       if (userField.value === '') {" +
                "           userField.value = AndroidAutofill.getSavedUser();" +
                "       }" +
                "       if (passField.value === '') {" +
                "           passField.value = AndroidAutofill.getSavedPassword();" +
                "       }" +

                "       function handleInput() {" +
                "           AndroidAutofill.saveCredentials(userField.value, passField.value);" +
                "       }" +

                "       userField.addEventListener('input', handleInput);" +
                "       passField.addEventListener('input', handleInput);" +
                "       return true;" +
                "   }" +
                "   return false;" +
                "}" +

                "if (!setupAutofill()) {" +
                "   const observer = new MutationObserver((mutations) => {" +
                "       if (setupAutofill()) {" +
                "           observer.disconnect();" +
                "       }" +
                "   });" +
                "   observer.observe(document.body, observerConfig);" +
                "}" +

                "document.querySelectorAll('.nav-link').forEach(tab => {" +
                "   tab.addEventListener('click', () => {" +
                "       setTimeout(setupAutofill, 300);" +
                "   });" +
                "});" +
                "})();";

        view.evaluateJavascript(script, null);
    }

    private void removeHeader(WebView view) {
        String js = "document.documentElement.style.webkitTouchCallout='none';" +
                "document.documentElement.style.webkitUserSelect='none';" +
                "var nav=document.querySelector('#page-content-wrapper > nav'); if(nav) nav.remove();" +
                "var sidebar=document.querySelector('#sidebar-wrapper'); if(sidebar) sidebar.remove();" +
                "var responsavelTab=document.querySelector('#responsavel-tab'); if(responsavelTab) responsavelTab.remove();" +
                "var alunoTab=document.querySelector('#aluno-tab'); if(alunoTab) alunoTab.remove();" +
                "var login=document.querySelector('#login'); if(login) login.remove();" +
                "var cardElement=document.querySelector('body > div.row.mx-0.pt-4 > div > div.card.mt-4.border-radius-card.border-0.shadow'); if(cardElement) cardElement.remove();" +
                "var backButton = document.querySelector('#page-content-wrapper > div.d-lg-flex > div.container-fluid.p-3 > div.card.bg-transparent.border-0 > div.card-body.px-0.px-md-3 > div:nth-child(1) > div.card-header.bg-soft-blue.border-left-blue.text-blue.rounded > i.fas.fa-chevron-left.btn-outline-primary.py-1.px-2.rounded.mr-2');" +
                "if (backButton) backButton.remove();" +
                "var darkHeader = document.querySelector('#page-content-wrapper > div.d-lg-flex > div.container-fluid.p-3 > div.card.bg-transparent.border-0 > div.card-header.bg-dark.rounded.d-flex.align-items-center.justify-content-center');" +
                "if (darkHeader) darkHeader.remove();" +
                "var style=document.createElement('style');" +
                "style.type='text/css';" +
                "style.appendChild(document.createTextNode('::-webkit-scrollbar{display:none;}'));" +
                "document.head.appendChild(style);";
        view.evaluateJavascript(js, null);
    }

    private void applyWebViewDarkMode(WebSettings settings) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(
                    settings,
                    isSystemDarkMode() ? WebSettingsCompat.FORCE_DARK_ON : WebSettingsCompat.FORCE_DARK_OFF
            );
            if (isSystemDarkMode() && WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                WebSettingsCompat.setForceDarkStrategy(settings,
                        WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY);
            }
        }
    }

    private void setupWebViewSecurity() {
        webView.setOnLongClickListener(v -> true);
        webView.setLongClickable(false);
        webView.setHapticFeedbackEnabled(false);
    }

    private boolean isSystemDarkMode() {
        int uiMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private void injectCssDarkMode(WebView view) {
        String css = "html{filter:invert(1) hue-rotate(180deg)!important; background:#121212!important;}" +
                "img,picture,video,iframe{filter:invert(1) hue-rotate(180deg)!important;}";
        String js = "(function(){var style=document.createElement('style'); style.textContent=\"" + css + "\"; document.head.appendChild(style);})();";
        view.evaluateJavascript(js, null);
    }

    private void showWebViewWithAnimation(WebView view) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            view.setAlpha(0f);
            view.setVisibility(View.VISIBLE);
            view.animate().alpha(1f).setDuration(300).start();
        }, 100);
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } else {
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }

    private void showNoInternetUI() {
        webView.setVisibility(View.GONE);
        layoutError.setVisibility(View.GONE);
        layoutSemInternet.setVisibility(View.VISIBLE);
        btnTentarNovamente.setOnClickListener(v -> {
            if (isOnline()) {
                layoutSemInternet.setVisibility(View.GONE);
                webView.reload();
            } else {
                Toast.makeText(requireContext(), "Sem conexão com a internet", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkStoragePermissions() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && !prefs.getBoolean(KEY_ASKED_STORAGE, false)) {
            prefs.edit().putBoolean(KEY_ASKED_STORAGE, true).apply();
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            }
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private void configureDownloadListener() {
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) return;
            String cookies = CookieManager.getInstance().getCookie(url);
            String referer = webView.getUrl();
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            downloadManually(url, fileName, cookies, userAgent, referer, mimeType);
        });
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private void downloadManually(String url, String fileName, String cookies, String userAgent, String referer, @Nullable String mimeType) {
        NotificationCompat.Builder notif = new NotificationCompat.Builder(requireContext(), CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Download")
                .setContentText("Baixando " + fileName)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);
        NotificationManagerCompat.from(requireContext()).notify(NOTIFICATION_ID, notif.build());

        Executors.newSingleThreadExecutor().execute(() -> {
            HttpURLConnection conn = null;
            InputStream in = null;
            OutputStream out = null;
            Uri targetUri = null;
            try {
                URL u = new URL(url);
                conn = (HttpURLConnection) u.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("Cookie", cookies);
                conn.setRequestProperty("User-Agent", userAgent);
                conn.setRequestProperty("Referer", referer);
                conn.connect();

                int code = conn.getResponseCode();
                if (code / 100 == 3) {
                    String loc = conn.getHeaderField("Location");
                    conn.disconnect();
                    downloadManually(loc, fileName, cookies, userAgent, referer, mimeType);
                    return;
                }
                if (code != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code);

                in = conn.getInputStream();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                    if (mimeType != null) values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    targetUri = requireContext().getContentResolver()
                            .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    out = requireContext().getContentResolver().openOutputStream(targetUri);
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File outFile = new File(dir, fileName);
                    out = new FileOutputStream(outFile);
                    targetUri = Uri.fromFile(outFile);
                }
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
                if (out != null) out.flush();

                Intent openIntent = new Intent(Intent.ACTION_VIEW);
                openIntent.setDataAndType(targetUri, mimeType != null ? mimeType : "*/*");
                openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                PendingIntent pi = PendingIntent.getActivity(requireContext(), 0, openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                notif.setContentText("Concluído: " + fileName)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentIntent(pi)
                        .setOngoing(false)
                        .setAutoCancel(true);
                NotificationManagerCompat.from(requireContext()).notify(NOTIFICATION_ID, notif.build());
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    requireContext().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, targetUri));
                }
            } catch (Exception e) {
                Log.e("DownloadManual", "erro", e);
                notif.setContentText("Falha: " + fileName)
                        .setSmallIcon(android.R.drawable.stat_notify_error)
                        .setOngoing(false)
                        .setAutoCancel(true);
                NotificationManagerCompat.from(requireContext()).notify(NOTIFICATION_ID, notif.build());
            } finally {
                if (conn != null) conn.disconnect();
                try { if (in != null) in.close(); } catch (IOException ignored) {}
                try { if (out != null) out.close(); } catch (IOException ignored) {}
            }
        });
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
            webView.destroy();
            webView = null;
        }
        super.onDestroyView();
    }

    public static Bundle createArgs(String url) {
        Bundle b = new Bundle();
        b.putString(ARG_URL, url);
        return b;
    }

    private void closeHomeFragment() {
        FragmentManager fm = requireActivity().getSupportFragmentManager();
        for (Fragment fragment : fm.getFragments()) {
            if (fragment instanceof HomeFragment) {
                ((HomeFragment) fragment).killFragment();
                break;
            }
        }
    }
}