package com.marinov.colegioetapa;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class MateriadeProva extends Fragment {
    private static final String ARG_URL = "url_prova";
    private static final String CACHE_PREFS = "materia_cache";
    private static final String CACHE_KEY_PREFIX = "materia_";

    private CircularProgressIndicator progressBar;
    private TextView txtErro, txtTitulo, txtConteudo;
    private CacheHelper cache;
    private String currentUrl;

    public static MateriadeProva newInstance(String url) {
        MateriadeProva fragment = new MateriadeProva();
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View root = inflater.inflate(R.layout.fragment_materia_prova, container, false);

        progressBar   = root.findViewById(R.id.progress_circular);
        txtErro       = root.findViewById(R.id.txt_erro);
        txtTitulo     = root.findViewById(R.id.txt_titulo);
        txtConteudo   = root.findViewById(R.id.txt_conteudo);

        cache = new CacheHelper(requireContext());
        currentUrl = getArguments() != null ? getArguments().getString(ARG_URL) : "";

        carregarConteudo();

        return root;
    }

    private void carregarConteudo() {
        // Sempre tenta carregar do cache primeiro
        String cachedData = cache.loadContent(currentUrl);
        if (cachedData != null) {
            exibirConteudoCache(cachedData);
        }

        // Faz requisição apenas se estiver online
        if (isOnline()) {
            new FetchContentTask().execute(currentUrl);
        } else if (cachedData == null) {
            exibirErro("Não há dados.");
        }
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            return (netInfo != null && netInfo.isConnected());
        } catch (Exception e) {
            return false;
        }
    }

    private void exibirConteudoCache(String cachedData) {
        String[] parts = cachedData.split("\\|\\|\\|", 2);
        if (parts.length == 2) {
            txtTitulo.setText(parts[0]);
            Spanned sp = HtmlCompat.fromHtml(parts[1], HtmlCompat.FROM_HTML_MODE_LEGACY);
            txtConteudo.setText(sp);
            txtConteudo.setLineSpacing(8, 1.0f);
            txtConteudo.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());

            txtTitulo.setVisibility(View.VISIBLE);
            txtConteudo.setVisibility(View.VISIBLE);
            txtErro.setVisibility(View.GONE);
        }
    }

    private class FetchContentTask extends AsyncTask<String, Void, Document> {
        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
            txtErro.setVisibility(View.GONE);
        }

        @Override
        protected Document doInBackground(String... urls) {
            try {
                CookieManager cm = CookieManager.getInstance();
                String cookieHeader = cm.getCookie(urls[0]);
                return Jsoup.connect(urls[0])
                        .header("Cookie", cookieHeader)
                        .userAgent("Mozilla/5.0")
                        .timeout(15000)
                        .get();
            } catch (Exception e) {
                Log.e("MateriadeProva", "Erro ao carregar conteúdo", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(Document doc) {
            progressBar.setVisibility(View.GONE);

            if (doc != null) {
                processarDocumento(doc);
            } else {
                verificarCacheFallback();
            }
        }

        private void processarDocumento(Document doc) {
            Element header = doc.selectFirst("div.card-header div");
            Element content = doc.selectFirst("div.card-body p.contato-info");

            if (content != null) {
                String titulo = header != null ? header.text().replace(" - ", " – ") : "";
                String conteudoHtml = content.html();

                cache.saveContent(currentUrl, titulo + "|||" + conteudoHtml);
                exibirConteudoCache(titulo + "|||" + conteudoHtml);
            } else {
                verificarCacheFallback();
            }
        }

        private void verificarCacheFallback() {
            String cachedData = cache.loadContent(currentUrl);
            if (cachedData != null) {
                exibirConteudoCache(cachedData);
            } else {
                exibirErro("Não foi possível carregar o conteúdo");
            }
        }
    }

    private void exibirErro(String mensagem) {
        txtErro.setText(mensagem);
        txtErro.setVisibility(View.VISIBLE);
        txtTitulo.setVisibility(View.GONE);
        txtConteudo.setVisibility(View.GONE);
    }

    private static class CacheHelper {
        private final SharedPreferences prefs;

        public CacheHelper(Context ctx) {
            prefs = ctx.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE);
        }

        public void saveContent(String url, String data) {
            String key = CACHE_KEY_PREFIX + url.hashCode();
            prefs.edit().putString(key, data).apply();
        }

        public String loadContent(String url) {
            String key = CACHE_KEY_PREFIX + url.hashCode();
            return prefs.getString(key, null);
        }
    }
}