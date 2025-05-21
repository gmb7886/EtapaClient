package com.marinov.colegioetapa;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

public class CalendarioProvas extends Fragment {
    private static final String URL_BASE = "https://areaexclusiva.colegioetapa.com.br/provas/datas";
    private RecyclerView recyclerProvas;
    private CircularProgressIndicator progressBar;
    private View barOffline;
    private TextView txtSemProvas, txtSemDados;
    private MaterialButton btnLogin;
    private ProvasAdapter adapter;
    private CacheHelper cache;
    private Spinner spinnerMes;
    private OnBackPressedCallback onBackPressedCallback;

    private int mesSelecionado = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_provas_calendar, container, false);

        recyclerProvas = root.findViewById(R.id.recyclerProvas);
        progressBar = root.findViewById(R.id.progress_circular);
        barOffline = root.findViewById(R.id.barOffline);
        txtSemProvas = root.findViewById(R.id.txt_sem_provas);
        txtSemDados = root.findViewById(R.id.txt_sem_dados);
        spinnerMes = root.findViewById(R.id.spinner_mes);
        btnLogin = root.findViewById(R.id.btnLogin);

        setupRecyclerView();
        configurarSpinnerMeses();
        cache = new CacheHelper(requireContext());
        carregarDadosParaMes();

        btnLogin.setOnClickListener(v -> {
            BottomNavigationView navView = requireActivity().findViewById(R.id.bottom_navigation);
            if (navView != null) {
                navView.setSelectedItemId(R.id.navigation_home);
            }
        });

        return root;
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        onBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                    BottomNavigationView bottomNav = requireActivity().findViewById(R.id.bottom_navigation);
                    bottomNav.setSelectedItemId(R.id.navigation_home);
            }
        };

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                onBackPressedCallback
        );
    }
    private void setupRecyclerView() {
        recyclerProvas.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ProvasAdapter(new ArrayList<>(), this);
        recyclerProvas.setAdapter(adapter);
    }

    private void configurarSpinnerMeses() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.meses_array,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMes.setAdapter(adapter);

        spinnerMes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mesSelecionado = position;
                carregarDadosParaMes();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void carregarDadosParaMes() {
        if (!isOnline()) {
            exibirBarraOffline();
            verificarCache();
            return;
        }

        String url = mesSelecionado == 0
                ? URL_BASE
                : URL_BASE + "?mes%5B%5D=" + mesSelecionado;

        new FetchProvasTask().execute(url);
    }

    private void verificarCache() {
        if (cache.temProvas(mesSelecionado)) {
            carregarCacheProvas();
        } else if (cache.mesSemProvas(mesSelecionado)) {
            exibirMensagemSemProvas();
        } else {
            exibirSemDados();
        }
    }
    private void exibirMensagemSemProvas() {
        recyclerProvas.setVisibility(View.GONE);
        txtSemProvas.setVisibility(View.VISIBLE);
        barOffline.setVisibility(View.GONE);
        txtSemDados.setVisibility(View.GONE);
    }
    private void carregarCacheProvas() {
        String html = cache.loadHtml(mesSelecionado);
        Document fake = Jsoup.parse(html);
        Element table = fake.selectFirst("table");
        if (table != null) {
            parseAndDisplayTable(table);
            recyclerProvas.setVisibility(View.VISIBLE);
        }
    }

    private class FetchProvasTask extends AsyncTask<String, Void, Document> {
        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
            recyclerProvas.setVisibility(View.GONE);
            txtSemProvas.setVisibility(View.GONE);
            barOffline.setVisibility(View.GONE);
            txtSemDados.setVisibility(View.GONE);
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
                Log.e("CalendarioProvas", "Erro na conexão", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(Document doc) {
            progressBar.setVisibility(View.GONE);

            if (doc != null) {
                Element table = doc.selectFirst("#page-content-wrapper > div.d-lg-flex > div.container-fluid.p-3 > div.card.bg-transparent.border-0 > div.card-body.px-0.px-md-3 > div.card.mb-5.bg-transparent.text-white.border-0 > table");
                Element alerta = doc.selectFirst("#page-content-wrapper > div.d-lg-flex > div.container-fluid.p-3 > div.card.bg-transparent.border-0 > div.card-body.px-0.px-md-3 > div.alert.alert-info.text-center");

                if (table != null) {
                    cache.salvarProvas(table.outerHtml(), mesSelecionado);
                    parseAndDisplayTable(table);
                    exibirConteudoOnline();
                } else if (alerta != null) {
                    cache.salvarMesSemProvas(mesSelecionado);
                    exibirMensagemSemProvas();
                } else {
                    verificarCache();
                    exibirBarraOffline();
                }
            } else {
                verificarCache();
                exibirBarraOffline();
            }
        }

        private void exibirConteudoOnline() {
            recyclerProvas.setVisibility(View.VISIBLE);
            barOffline.setVisibility(View.GONE);
            txtSemProvas.setVisibility(View.GONE);
            txtSemDados.setVisibility(View.GONE);
        }

        private void exibirMensagemSemProvas() {
            recyclerProvas.setVisibility(View.GONE);
            txtSemProvas.setVisibility(View.VISIBLE);
            barOffline.setVisibility(View.GONE);
            txtSemDados.setVisibility(View.GONE);
        }
    }

    private void exibirBarraOffline() {
        barOffline.setVisibility(View.VISIBLE);
    }

    private void exibirSemDados() {
        recyclerProvas.setVisibility(View.GONE);
        txtSemProvas.setVisibility(View.GONE);
        barOffline.setVisibility(View.GONE);
        txtSemDados.setVisibility(View.VISIBLE);
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

    private void parseAndDisplayTable(Element table) {
        List<ProvaItem> items = new ArrayList<>();
        Elements rows = table.select("tbody > tr");

        for (Element tr : rows) {
            Elements cells = tr.children();
            if (cells.size() < 5) continue;

            String data = cells.get(0).text();
            String codigo = cells.get(1).ownText();
            Element linkElement = cells.get(1).selectFirst("a");
            String link = (linkElement != null) ? linkElement.attr("href") : "";
            String tipo = cells.get(2).text();
            String conjunto = String.format("%s° conjunto", cells.get(3).text());
            String materia = cells.get(4).text();

            if (!data.isEmpty() && !codigo.isEmpty()) {
                items.add(new ProvaItem(data, codigo, link, tipo, conjunto, materia));
            }
        }

        adapter.updateData(items);
    }

    private static class ProvasAdapter extends RecyclerView.Adapter<ProvasAdapter.ViewHolder> {
        private List<ProvaItem> items;
        private final Fragment parentFragment;

        public ProvasAdapter(List<ProvaItem> items, Fragment parentFragment) {
            this.items = items;
            this.parentFragment = parentFragment;
        }

        public void updateData(List<ProvaItem> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_prova_calendar, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ProvaItem item = items.get(position);

            holder.txtData.setText(item.data);
            holder.txtCodigo.setText(item.codigo);
            holder.txtConjunto.setText(item.conjunto);
            holder.txtMateria.setText(item.materia);

            holder.btnTipo.setText(item.tipo);
            holder.btnTipo.setBackgroundResource(item.tipo.toLowerCase().contains("rec")
                    ? R.drawable.bg_warning_rounded
                    : R.drawable.bg_primary_rounded);

            holder.card.setOnClickListener(v -> {
                if (parentFragment.isAdded()) {
                    FragmentTransaction transaction = parentFragment.getParentFragmentManager().beginTransaction();
                    transaction.replace(R.id.nav_host_fragment, MateriadeProva.newInstance(item.link));
                    transaction.addToBackStack(null);
                    transaction.commit();
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView card;
            TextView txtData, txtCodigo, txtConjunto, txtMateria;
            MaterialButton btnTipo;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.card_prova);
                txtData = itemView.findViewById(R.id.txt_data);
                txtCodigo = itemView.findViewById(R.id.txt_codigo);
                txtConjunto = itemView.findViewById(R.id.txt_conjunto);
                txtMateria = itemView.findViewById(R.id.txt_materia);
                btnTipo = itemView.findViewById(R.id.btn_tipo);
            }
        }
    }

    private static class ProvaItem {
        String data, codigo, link, tipo, conjunto, materia;

        public ProvaItem(String data, String codigo, String link, String tipo, String conjunto, String materia) {
            this.data = data;
            this.codigo = codigo;
            this.link = link;
            this.tipo = tipo;
            this.conjunto = conjunto;
            this.materia = materia;
        }
    }

    private static class CacheHelper {
        private static final String PREFS = "calendario_prefs";
        private static final String KEY_BASE = "cache_html_calendario_";
        private static final String KEY_SEM_PROVAS = "sem_provas_";
        private final SharedPreferences prefs;

        public CacheHelper(Context ctx) {
            prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }

        public void salvarProvas(String html, int mes) {
            prefs.edit()
                    .putString(KEY_BASE + mes, html)
                    .remove(KEY_SEM_PROVAS + mes)
                    .apply();
        }

        public void salvarMesSemProvas(int mes) {
            prefs.edit()
                    .putBoolean(KEY_SEM_PROVAS + mes, true)
                    .remove(KEY_BASE + mes)
                    .apply();
        }

        public String loadHtml(int mes) {
            return prefs.getString(KEY_BASE + mes, null);
        }

        public boolean temProvas(int mes) {
            return prefs.contains(KEY_BASE + mes);
        }

        public boolean mesSemProvas(int mes) {
            return prefs.getBoolean(KEY_SEM_PROVAS + mes, false);
        }
    }
}