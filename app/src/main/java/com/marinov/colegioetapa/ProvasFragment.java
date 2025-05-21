package com.marinov.colegioetapa;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
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
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ProvasFragment extends Fragment {
    private static final String TAG = "ProvasFragment";
    private OnBackPressedCallback onBackPressedCallback;
    private SearchView searchView;
    private RecyclerView recyclerProvas;
    private CircularProgressIndicator progressBar;
    private ProvasAdapter adapter;
    private final List<RepoItem> allItems = new ArrayList<>();
    private String currentPath = "";
    private FetchFilesTask fetchTask;
    private LinearLayout layoutSemInternet;
    private MaterialButton btnTentarNovamente;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 1) Se API < 24 (Android 7.0), exibe Toast e bloqueia o fragment:
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Toast.makeText(requireContext(),
                            "Essa função só está disponível em Android 7.0 ou superior",
                            Toast.LENGTH_LONG)
                    .show();
            return new View(requireContext());
        }

        // 2) Caso contrário, infla o layout e inicializa tudo:
        View view = inflater.inflate(R.layout.fragment_provas, container, false);
        layoutSemInternet = view.findViewById(R.id.layout_sem_internet);
        btnTentarNovamente = view.findViewById(R.id.btn_tentar_novamente);
        searchView = view.findViewById(R.id.search_view);
        recyclerProvas = view.findViewById(R.id.recyclerProvas);
        progressBar = view.findViewById(R.id.progress_circular);

        // Configuração da SearchView
        searchView.setQueryHint("Buscar provas...");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { return false; }
            @Override public boolean onQueryTextChange(String newText) {
                filterList(newText);
                return true;
            }
        });

        recyclerProvas.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ProvasAdapter(new ArrayList<>(), ProvasFragment.this::onItemClick);
        recyclerProvas.setAdapter(adapter);

        if (hasInternetConnection()) {
            checkAuthentication();
        } else {
            showNoInternetUI();
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (fetchTask != null) fetchTask.cancel(true);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void checkAuthentication() {
        WebView authCheckWebView = new WebView(requireContext());
        CookieManager.getInstance().setAcceptThirdPartyCookies(authCheckWebView, true);
        WebSettings authSettings = authCheckWebView.getSettings();
        authSettings.setJavaScriptEnabled(true);
        authSettings.setDomStorageEnabled(true);

        authCheckWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(
                        "(function() { " +
                                "return document.querySelector('#page-content-wrapper > div.d-lg-flex > div.container-fluid.p-3 > " +
                                "div.card.bg-transparent.border-0 > div.card-body.px-0.px-md-3 > div:nth-child(2) > div.card-body > table') !== null; " +
                                "})();",
                        value -> {
                            boolean isAuthenticated = "true".equals(value);
                            if (isAuthenticated) {
                                startFetch();
                            } else {
                                showNoInternetUI();
                            }
                            authCheckWebView.destroy();
                        }
                );
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                showNoInternetUI();
                authCheckWebView.destroy();
            }
        });
        authCheckWebView.loadUrl("https://areaexclusiva.colegioetapa.com.br/provas/notas");
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        onBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!currentPath.isEmpty()) {
                    // Voltar para a pasta pai
                    currentPath = getParentPath(currentPath);
                    startFetch();
                } else {
                    // Se estiver na raiz, navegar para o HomeFragment
                    navigateToHomeFragment();
                    setEnabled(false); // Opcional, dependendo do fluxo
                }
            }
        };

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                onBackPressedCallback
        );
    }

    // Método para navegar ao HomeFragment
    private void navigateToHomeFragment() {
        BottomNavigationView bottomNav = requireActivity().findViewById(R.id.bottom_navigation);
        bottomNav.setSelectedItemId(R.id.navigation_home);
    }
    private String getParentPath(String path) {
        int lastSlash = path.lastIndexOf('/');
        return (lastSlash == -1) ? "" : path.substring(0, lastSlash);
    }
    private void showNoInternetUI() {
        recyclerProvas.setVisibility(View.GONE);
        searchView.setVisibility(View.GONE);
        layoutSemInternet.setVisibility(View.VISIBLE);

        btnTentarNovamente.setOnClickListener(v -> {
            if (hasInternetConnection()) {
                layoutSemInternet.setVisibility(View.GONE);
                checkAuthentication();
            }
        });
    }

    private boolean hasInternetConnection() {
        ConnectivityManager cm = (ConnectivityManager) requireContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } else {
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            return netInfo != null && netInfo.isConnected();
        }
    }

    private void startFetch() {
        recyclerProvas.setVisibility(View.VISIBLE);
        searchView.setVisibility(View.VISIBLE);
        if (fetchTask != null) fetchTask.cancel(true);
        fetchTask = new FetchFilesTask(this);
        fetchTask.execute(currentPath);
    }

    private void onItemClick(RepoItem item) {
        if ("dir".equals(item.type)) {
            currentPath = item.path;
            startFetch();
        } else {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(item.downloadUrl));
            request.setMimeType("*/*");
            request.addRequestHeader("User-Agent", "EtapaApp");
            request.setTitle(item.name);
            request.setDescription("Baixando arquivo...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, item.name);

            DownloadManager dm = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) dm.enqueue(request);
        }
    }

    private void filterList(String query) {
        String lower = (query == null) ? "" : query.toLowerCase();
        List<RepoItem> filtered = allItems.stream()
                .filter(i -> i.name.toLowerCase().contains(lower))
                .collect(Collectors.toList());
        adapter.updateData(filtered);
    }

    void onFetchCompleted(List<RepoItem> results) {
        progressBar.setVisibility(View.GONE);
        allItems.clear();
        allItems.addAll(results);
        adapter.updateData(results);
    }

    private static class RepoItem {
        String name, type, path, downloadUrl;
        RepoItem(String name, String type, String path, String downloadUrl) {
            this.name = name;
            this.type = type;
            this.path = path;
            this.downloadUrl = downloadUrl;
        }
    }

    private static class ProvasAdapter extends RecyclerView.Adapter<ProvasAdapter.VH> {
        interface OnItemClick { void onClick(RepoItem item); }
        private List<RepoItem> items;
        private final OnItemClick listener;

        ProvasAdapter(List<RepoItem> items, OnItemClick listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_prova, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            RepoItem item = items.get(position);
            holder.text.setText(item.name);
            holder.icon.setImageResource(
                    item.type.equals("dir") ? R.drawable.ic_folder : R.drawable.ic_file
            );
            holder.itemView.setOnClickListener(v -> listener.onClick(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @SuppressLint("NotifyDataSetChanged")
        void updateData(List<RepoItem> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        static class VH extends RecyclerView.ViewHolder {
            android.widget.ImageView icon;
            android.widget.TextView text;

            VH(View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.item_icon);
                text = itemView.findViewById(R.id.item_text);
            }
        }
    }

    private static class FetchFilesTask extends AsyncTask<String, Void, List<RepoItem>> {
        private final WeakReference<ProvasFragment> fragRef;
        private static final String GITHUB_TOKEN = BuildConfig.GITHUB_PAT;

        FetchFilesTask(ProvasFragment frag) { fragRef = new WeakReference<>(frag); }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            ProvasFragment f = fragRef.get();
            if (f != null && f.isAdded()) f.progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected List<RepoItem> doInBackground(String... params) {
            String path = params.length > 0 ? params[0] : "";
            List<RepoItem> list = new ArrayList<>();
            HttpURLConnection conn = null;
            try {
                String api = "https://api.github.com/repos/gmb7886/schooltests/contents"
                        + (path.isEmpty() ? "" : "/" + path);
                URL url = new URL(api);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "EtapaApp");
                conn.setRequestProperty("Authorization", "token " + GITHUB_TOKEN);

                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return list;

                InputStream is = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONArray arr = new JSONArray(sb.toString());
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    list.add(new RepoItem(
                            o.getString("name"),
                            o.getString("type"),
                            o.getString("path"),
                            o.optString("download_url", "")
                    ));
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao buscar arquivos", e);
            } finally {
                if (conn != null) conn.disconnect();
            }
            return list;
        }

        @Override
        protected void onPostExecute(List<RepoItem> result) {
            super.onPostExecute(result);
            ProvasFragment f = fragRef.get();
            if (f != null && f.isAdded()) f.onFetchCompleted(result);
        }
    }
}