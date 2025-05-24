package com.marinov.colegioetapa;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class NotasFragment extends Fragment {
    private static final String URL_NOTAS =
            "https://areaexclusiva.colegioetapa.com.br/provas/notas";
    private TableLayout tableNotas;
    private LinearLayout barOffline;
    private CacheHelper cache;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_notas, container, false);
        tableNotas = root.findViewById(R.id.tableNotas);
        barOffline = root.findViewById(R.id.barOffline);
        Button btnLogin = root.findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(v -> navigateToHome());

        cache = new CacheHelper(requireContext());
        new FetchNotasTask().execute(URL_NOTAS);
        return root;
    }

    @SuppressLint("StaticFieldLeak")
    private class FetchNotasTask extends AsyncTask<String, Void, Document> {
        @Override
        protected Document doInBackground(String... urls) {
            try {
                CookieManager cm = CookieManager.getInstance();
                String cookieHeader = cm.getCookie(URL_NOTAS);
                return Jsoup.connect(urls[0])
                        .header("Cookie", cookieHeader)
                        .userAgent("Mozilla/5.0")
                        .timeout(15000)
                        .get();
            } catch (Exception e) {
                Log.e("NotasFragment", "Erro ao conectar", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(Document doc) {
            if (doc != null) {
                Element newTable = doc.selectFirst(
                        "#page-content-wrapper > div.d-lg-flex > div.container-fluid.p-3 > " +
                                "div.card.bg-transparent.border-0 > div.card-body.px-0.px-md-3 > " +
                                "div:nth-child(2) > div.card-body > table"
                );
                if (newTable != null) {
                    cache.saveHtml(newTable.outerHtml());
                    parseAndBuildTable(newTable);
                    hideOfflineBar();
                } else {
                    showOfflineBar();
                    Log.e("NotasFragment", "Tabela não encontrada no HTML");
                    carregarCacheSeExistir();
                }
            } else {
                carregarCacheSeExistir();
                showOfflineBar();
                Log.e("NotasFragment", "Falha na conexão — usando cache");
            }
        }

        private void carregarCacheSeExistir() {
            String html = cache.loadHtml();
            if (html != null) {
                Document fake = Jsoup.parse(html);
                Element table = fake.selectFirst("table");
                if (table != null) {
                    parseAndBuildTable(table);
                }
            }
        }
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Configurar o callback do botão voltar
        // Simula um clique no item "Início" da BottomNavigation
        OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Simula um clique no item "Início" da BottomNavigation
                BottomNavigationView bottomNav = requireActivity().findViewById(R.id.bottom_navigation);
                bottomNav.setSelectedItemId(R.id.navigation_home);
            }
        };

        // Registrar o callback no dispatcher
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                onBackPressedCallback
        );
    }
    private void parseAndBuildTable(Element table) {
        tableNotas.removeAllViews();
        int colorDefault = ContextCompat.getColor(requireContext(), R.color.colorOnSurface);
        int colorHeaderBg = ContextCompat.getColor(requireContext(), R.color.header_bg);
        int colorHeaderText = ContextCompat.getColor(requireContext(), R.color.colorOnSurface);

        // Cabeçalho: ignora "Matéria", mostra Código + Conjuntos
        Elements headers = table.select("thead th");
        if (!headers.isEmpty()) {
            TableRow headerRow = new TableRow(requireContext());
            headerRow.setBackgroundColor(colorHeaderBg);
            // Código
            TextView thCode = createCell(headers.get(1).text(), true);
            thCode.setTextColor(colorHeaderText);
            headerRow.addView(thCode);
            // Demais colunas
            for (int i = 2; i < headers.size(); i++) {
                TextView th = createCell(headers.get(i).text(), true);
                th.setTextColor(colorHeaderText);
                headerRow.addView(th);
            }
            tableNotas.addView(headerRow);
        }

        // Dados e acumulação
        Elements rows = table.select("tbody > tr");
        int notaCols = headers.size() > 2 ? headers.size() - 2 : 0;
        double[] sums = new double[notaCols];
        int[] counts = new int[notaCols];

        for (Element r : rows) {
            TableRow row = new TableRow(requireContext());
            row.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.transparent));
            Elements cols = r.children();
            // Código (col 1)
            String code = cols.size() > 1 ? cols.get(1).text() : "";
            TextView tvCode = createCell(code, false);
            tvCode.setTextColor(colorDefault);
            row.addView(tvCode);
            // Notas
            for (int j = 2; j < cols.size(); j++) {
                String val = cols.get(j).text();
                if (!"--".equals(val)) {
                    try {
                        double d = Double.parseDouble(val);
                        sums[j-2] += d;
                        counts[j-2]++;
                    } catch (NumberFormatException ignored){}
                }
                TextView tv = createCell(val, false);
                tv.setTextColor(colorDefault);
                if (cols.get(j).hasClass("bg-success")) {
                    tv.setBackgroundResource(R.drawable.bg_success_rounded);
                    tv.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white));
                } else if (cols.get(j).hasClass("bg-warning")) {
                    tv.setBackgroundResource(R.drawable.bg_warning_rounded);
                    tv.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black));
                } else if (cols.get(j).hasClass("bg-danger")) {
                    tv.setBackgroundResource(R.drawable.bg_danger_rounded);
                    tv.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white));
                }
                row.addView(tv);
            }
            tableNotas.addView(row);
        }

        // Linha de Médias
        if (!headers.isEmpty()) {
            TableRow avg = new TableRow(requireContext());
            avg.setBackgroundColor(colorHeaderBg);
            TextView first = createCell("Média", true);
            first.setTextColor(colorHeaderText);
            avg.addView(first);
            for (int k = 0; k < notaCols; k++) {
                @SuppressLint("DefaultLocale") String m = counts[k] > 0 ? String.format("%.2f", sums[k]/counts[k]) : "--";
                TextView tvm = createCell(m, true);
                tvm.setTextColor(colorHeaderText);
                avg.addView(tvm);
            }
            tableNotas.addView(avg);
        }
    }

    private void showOfflineBar() { barOffline.setVisibility(View.VISIBLE); }
    private void hideOfflineBar() { barOffline.setVisibility(View.GONE); }

    private void navigateToHome() {
        try {
            BottomNavigationView nav = requireActivity().findViewById(R.id.bottom_navigation);
            nav.setSelectedItemId(R.id.navigation_home);
        } catch (Exception e) {
            Log.e("NotasFragment","Erro ao simular retorno à home",e);
        }
    }

    private TextView createCell(String txt, boolean isHeader) {
        TextView tv = new TextView(requireContext());
        tv.setText(txt);
        tv.setTypeface(null, isHeader ? Typeface.BOLD : Typeface.NORMAL);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, isHeader?13:12);
        int h = (int)(8*getResources().getDisplayMetrics().density);
        int v = (int)(6*getResources().getDisplayMetrics().density);
        tv.setPadding(h,v,h,v);
        tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        return tv;
    }

    private static class CacheHelper {
        private static final String PREFS = "notas_prefs";
        private static final String KEY_HTML = "cache_html";
        private final SharedPreferences prefs;
        CacheHelper(Context ctx){ prefs=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
        void saveHtml(String html){ prefs.edit().putString(KEY_HTML,html).apply();}
        String loadHtml(){ return prefs.getString(KEY_HTML,null);}    }
}
