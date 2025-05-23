package com.marinov.colegioetapa;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private ViewPager2 viewPager;
    private RecyclerView newsRecyclerView;
    private LinearLayout layoutSemInternet;
    private MaterialButton btnTentarNovamente;
    private View loadingContainer;
    private View contentContainer;

    private final List<CarouselItem> carouselItems = new ArrayList<>();
    private final List<NewsItem> newsItems = new ArrayList<>();
    private static final String HOME_URL = "https://areaexclusiva.colegioetapa.com.br/home";
    private static final String OUT_URL = "https://areaexclusiva.colegioetapa.com.br";

    private boolean isFragmentDestroyed = false;
    private boolean shouldReloadOnResume = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.home_fragment_new, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        setupRecyclerView();
        setupListeners();
        checkInternetAndLoadData();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (shouldReloadOnResume) {
            checkInternetAndLoadData();
            shouldReloadOnResume = false;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isFragmentDestroyed = true;
    }

    private void initializeViews(View view) {
        loadingContainer = view.findViewById(R.id.loadingContainer);
        contentContainer = view.findViewById(R.id.contentContainer);
        layoutSemInternet = view.findViewById(R.id.layout_sem_internet);
        btnTentarNovamente = view.findViewById(R.id.btn_tentar_novamente);
        viewPager = view.findViewById(R.id.viewPager);
        newsRecyclerView = view.findViewById(R.id.newsRecyclerView);
    }

    private void setupRecyclerView() {
        newsRecyclerView.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false)
        );
    }

    private void setupListeners() {
        btnTentarNovamente.setOnClickListener(v -> checkInternetAndLoadData());
    }

    private void checkInternetAndLoadData() {
        if (hasInternetConnection()) {
            showLoadingState();
            new Thread(this::fetchAndProcessData).start();
        } else {
            showOfflineState();
        }
    }

    private void fetchAndProcessData() {
        try {
            Document doc = fetchHomePageData();
            if (isValidSession(doc)) {
                processPageContent(doc);
                updateUIOnSuccess();
            } else {
                handleInvalidSession();
            }
        } catch (IOException e) {
            handleDataFetchError(e);
        }
    }

    private Document fetchHomePageData() throws IOException {
        CookieManager cookieManager = CookieManager.getInstance();
        String cookies = cookieManager.getCookie(HOME_URL);

        return Jsoup.connect(HOME_URL)
                .userAgent("Mozilla/5.0")
                .header("Cookie", cookies != null ? cookies : "")
                .timeout(15000)
                .get();
    }

    private boolean isValidSession(Document doc) {
        return doc.getElementById("home_banners_carousel") != null &&
                doc.selectFirst("div.col-12.col-lg-8.mb-5") != null;
    }

    private void processPageContent(Document doc) {
        carouselItems.clear();
        newsItems.clear();
        processCarousel(doc);
        processNews(doc);
    }

    private void updateUIOnSuccess() {
        requireActivity().runOnUiThread(() -> {
            if (carouselItems.isEmpty() && newsItems.isEmpty()) {
                navigateToWebView(OUT_URL);
            } else {
                showContentState();
                setupCarousel();
                setupNews();
            }
        });
    }

    private void handleInvalidSession() {
        requireActivity().runOnUiThread(() -> {
            navigateToWebView(OUT_URL);
            shouldReloadOnResume = true;
        });
    }

    private void handleDataFetchError(IOException e) {
        Log.e("HomeFragment", "Erro ao buscar dados: " + e.getMessage());
        requireActivity().runOnUiThread(() -> {
            if (carouselItems.isEmpty() && newsItems.isEmpty()) {
                navigateToWebView(OUT_URL);
                shouldReloadOnResume = true;
            } else {
                showOfflineState();
            }
        });
    }

    private void processCarousel(Document doc) {
        Element carousel = doc.getElementById("home_banners_carousel");
        if (carousel == null) return;

        Elements items = carousel.select(".carousel-item");
        for (Element item : items) {
            if (isFragmentDestroyed) return;

            Element linkElem = item.selectFirst("a");
            String linkUrl = linkElem != null ? linkElem.attr("href") : "";
            String imgUrl = item.select("img").attr("src");

            if (!imgUrl.startsWith("http")) {
                imgUrl = "https://www.colegioetapa.com.br" + imgUrl;
            }

            carouselItems.add(new CarouselItem(imgUrl, linkUrl));
        }
    }

    private void processNews(Document doc) {
        Element newsSection = doc.selectFirst("div.col-12.col-lg-8.mb-5");
        if (newsSection == null) return;

        Elements cards = newsSection.select(".card.border-radius-card");
        cards.removeAll(newsSection.select("#modal-avisos-importantes .card.border-radius-card"));

        for (Element card : cards) {
            String iconUrl = card.select("img.aviso-icon").attr("src");
            String title = card.select("p.text-blue.aviso-text").text();
            String desc = card.select("p.m-0.aviso-text").text();
            String link = card.select("a[target=_blank]").attr("href");

            if (!iconUrl.startsWith("http")) {
                iconUrl = "https://areaexclusiva.colegioetapa.com.br" + iconUrl;
            }

            if (!isDuplicateNews(title)) {
                newsItems.add(new NewsItem(iconUrl, title, desc, link));
            }
        }
    }

    private boolean isDuplicateNews(String title) {
        for (NewsItem ni : newsItems) {
            if (ni.getTitle().equals(title)) {
                return true;
            }
        }
        return false;
    }

    private void navigateToWebView(String url) {
        WebViewFragment fragment = new WebViewFragment();
        fragment.setArguments(WebViewFragment.createArgs(url));
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void showLoadingState() {
        requireActivity().runOnUiThread(() -> {
            loadingContainer.setVisibility(View.VISIBLE);
            contentContainer.setVisibility(View.GONE);
            layoutSemInternet.setVisibility(View.GONE);
        });
    }

    private void showContentState() {
        requireActivity().runOnUiThread(() -> {
            loadingContainer.setVisibility(View.GONE);
            contentContainer.setVisibility(View.VISIBLE);
            layoutSemInternet.setVisibility(View.GONE);
        });
    }

    private void showOfflineState() {
        requireActivity().runOnUiThread(() -> {
            loadingContainer.setVisibility(View.GONE);
            contentContainer.setVisibility(View.GONE);
            layoutSemInternet.setVisibility(View.VISIBLE);
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

    private void setupCarousel() {
        viewPager.setAdapter(new CarouselAdapter());
        viewPager.setPageTransformer((page, position) -> {
            float offset = position * -60f;
            page.setTranslationX(offset);
        });
    }

    private void setupNews() {
        newsRecyclerView.setAdapter(new NewsAdapter());
    }

    private class CarouselAdapter extends RecyclerView.Adapter<CarouselViewHolder> {
        @NonNull
        @Override
        public CarouselViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_carousel, parent, false);
            return new CarouselViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull CarouselViewHolder holder, int position) {
            CarouselItem item = carouselItems.get(position);
            Glide.with(holder.itemView.getContext())
                    .load(item.getImageUrl())
                    .centerCrop()
                    .into(holder.imageView);
            holder.itemView.setOnClickListener(v ->
                    navigateToWebView(item.getLinkUrl())
            );
        }

        @Override
        public int getItemCount() {
            return carouselItems.size();
        }
    }

    private class NewsAdapter extends RecyclerView.Adapter<NewsViewHolder> {
        @NonNull
        @Override
        public NewsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.news_item, parent, false);
            return new NewsViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull NewsViewHolder holder, int position) {
            NewsItem item = newsItems.get(position);
            Glide.with(holder.itemView.getContext())
                    .load(item.getIconUrl())
                    .into(holder.icon);
            holder.title.setText(item.getTitle());
            holder.description.setText(item.getDescription());
            holder.itemView.setOnClickListener(v ->
                    navigateToWebView(item.getLink())
            );
        }

        @Override
        public int getItemCount() {
            return newsItems.size();
        }
    }

    static class CarouselViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        CarouselViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
        }
    }

    static class NewsViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView title, description;

        NewsViewHolder(View view) {
            super(view);
            icon = view.findViewById(R.id.news_icon);
            title = view.findViewById(R.id.news_title);
            description = view.findViewById(R.id.news_description);
        }
    }

    static class CarouselItem {
        private final String imageUrl, linkUrl;

        CarouselItem(String imageUrl, String linkUrl) {
            this.imageUrl = imageUrl;
            this.linkUrl = linkUrl;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public String getLinkUrl() {
            return linkUrl;
        }
    }

    static class NewsItem {
        private final String iconUrl, title, description, link;

        NewsItem(String iconUrl, String title,
                 String description, String link) {
            this.iconUrl = iconUrl;
            this.title = title;
            this.description = description;
            this.link = link;
        }

        public String getIconUrl() {
            return iconUrl;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public String getLink() {
            return link;
        }
    }
}