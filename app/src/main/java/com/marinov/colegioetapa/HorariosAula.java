package com.marinov.colegioetapa;

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
import com.google.android.material.button.MaterialButton;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class HorariosAula extends Fragment {

    private static final String URL_HORARIOS = "https://areaexclusiva.colegioetapa.com.br/horarios/aulas";
    private TableLayout tableHorarios;
    private LinearLayout barOffline;

    private CacheHelper cache;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_horarios, container, false);
        tableHorarios = root.findViewById(R.id.tableHorarios);
        barOffline = root.findViewById(R.id.barOffline);
        MaterialButton btnLogin = root.findViewById(R.id.btnLogin);
        cache = new CacheHelper(requireContext());
        new FetchHorariosTask().execute(URL_HORARIOS);
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
    private class FetchHorariosTask extends AsyncTask<String, Void, Document> {
        @Override
        protected Document doInBackground(String... urls) {
            try {
                CookieManager cm = CookieManager.getInstance();
                String cookieHeader = cm.getCookie(URL_HORARIOS);
                return Jsoup.connect(urls[0])
                        .header("Cookie", cookieHeader)
                        .userAgent("Mozilla/5.0")
                        .timeout(15000)
                        .get();
            } catch (Exception e) {
                Log.e("HorariosAula", "Erro ao conectar", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(Document doc) {
            if (doc != null) {
                Element table = doc.selectFirst(
                        "#page-content-wrapper > div.d-lg-flex > div.container-fluid.p-3 > div.card.bg-transparent.border-0 " +
                                "> div.card-body.px-0.px-md-3 > div > div.card-body > table"
                );

                if (table != null) {
                    cache.saveHtml(table.outerHtml());
                    parseAndBuildTable(table);
                    hideOfflineBar();
                } else {
                    showOfflineBar();
                    Log.e("HorariosAula", "Tabela não encontrada no HTML");
                    carregarCacheSeExistir();
                }
            } else {
                carregarCacheSeExistir();
                showOfflineBar();
                Log.e("HorariosAula", "Falha na conexão — usando cache");
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

    private void parseAndBuildTable(Element table) {
        tableHorarios.removeAllViews();
        int headerBgColor = ContextCompat.getColor(requireContext(), R.color.colorPrimary);
        int textColor = ContextCompat.getColor(requireContext(), R.color.colorOnSurface);

        // Cabeçalho
        Element headerRowHtml = table.selectFirst("thead > tr");
        if (headerRowHtml != null) {
            TableRow headerRow = new TableRow(getContext());
            headerRow.setBackgroundColor(headerBgColor);
            for (Element th : headerRowHtml.select("th")) {
                TextView tv = createCell(th.text(), true);
                tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorOnPrimary));
                headerRow.addView(tv);
            }
            tableHorarios.addView(headerRow);
        }

        // Linhas de dados
        Elements rows = table.select("tbody > tr");
        for (Element tr : rows) {
            if (!tr.select("div.alert-info").isEmpty()) continue;

            TableRow row = new TableRow(getContext());
            row.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.transparent));

            for (Element cell : tr.children()) {
                boolean isHeaderCell = cell.tagName().equals("th");
                TextView tv = createCell(cell.text(), isHeaderCell);
                tv.setTextColor(textColor);

                if (cell.hasClass("bg-primary")) {
                    tv.setBackgroundResource(R.drawable.bg_primary_rounded);
                    tv.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white));
                }
                row.addView(tv);
            }
            tableHorarios.addView(row);
        }
    }

    private TextView createCell(String text, boolean isHeader) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, isHeader ? 14 : 13);
        tv.setTypeface(null, isHeader ? Typeface.BOLD : Typeface.NORMAL);

        int padH = (int) (12 * getResources().getDisplayMetrics().density);
        int padV = (int) (8 * getResources().getDisplayMetrics().density);
        tv.setPadding(padH, padV, padH, padV);

        int minWidth = (int) (80 * getResources().getDisplayMetrics().density);
        tv.setMinWidth(minWidth);

        TableRow.LayoutParams lp = new TableRow.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        lp.setMargins(2, 2, 2, 2);
        tv.setLayoutParams(lp);

        tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        return tv;
    }

    private void showOfflineBar() {
        barOffline.setVisibility(View.VISIBLE);
    }

    private void hideOfflineBar() {
        barOffline.setVisibility(View.GONE);
    }

    private static class CacheHelper {
        private static final String PREFS = "horarios_prefs";
        private static final String KEY_HTML = "cache_html_horarios";
        private final SharedPreferences prefs;

        CacheHelper(Context ctx) {
            prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }

        void saveHtml(String html) {
            prefs.edit().putString(KEY_HTML, html).apply();
        }

        String loadHtml() {
            return prefs.getString(KEY_HTML, null);
        }
    }
}