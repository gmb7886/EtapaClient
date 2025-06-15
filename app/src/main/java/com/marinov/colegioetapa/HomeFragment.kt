package com.marinov.colegioetapa

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.marinov.colegioetapa.WebViewFragment.Companion.createArgs
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import androidx.core.view.isVisible
import androidx.core.content.edit

class HomeFragment : Fragment() {
    private var viewPager: ViewPager2? = null
    private var newsRecyclerView: RecyclerView? = null
    private var layoutSemInternet: LinearLayout? = null
    private var btnTentarNovamente: MaterialButton? = null
    private var loadingContainer: View? = null
    private var contentContainer: View? = null
    private var shouldBlockNavigation = false
    private val carouselItems: MutableList<CarouselItem> = ArrayList<CarouselItem>()
    private val newsItems: MutableList<NewsItem> = ArrayList<NewsItem>()
    private var txtStuckHint: TextView? = null

    private var isFragmentDestroyed = false
    private var shouldReloadOnResume = false

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.home_fragment_new, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        shouldBlockNavigation = false
        initializeViews(view)
        setupRecyclerView()
        setupListeners()
        checkInternetAndLoadData()
    }

    override fun onPause() {
        super.onPause()
        shouldBlockNavigation = true
    }

    override fun onResume() {
        super.onResume()
        shouldBlockNavigation = false
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Recalcula o tamanho do carrossel quando a orientação muda
        if (viewPager != null && viewPager!!.adapter != null) {
            viewPager!!.post(Runnable { this.adjustCarouselHeight() })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isFragmentDestroyed = true
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }

    private fun initializeViews(view: View) {
        loadingContainer = view.findViewById<View>(R.id.loadingContainer)
        contentContainer = view.findViewById<View>(R.id.contentContainer)
        layoutSemInternet = view.findViewById<LinearLayout>(R.id.layout_sem_internet)
        btnTentarNovamente = view.findViewById<MaterialButton>(R.id.btn_tentar_novamente)
        viewPager = view.findViewById<ViewPager2?>(R.id.viewPager)
        newsRecyclerView = view.findViewById<RecyclerView>(R.id.newsRecyclerView)
        txtStuckHint = view.findViewById<TextView>(R.id.txtStuckHint)
    }

    private fun setupRecyclerView() {
        newsRecyclerView!!.setLayoutManager(
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        )
    }

    private fun setupListeners() {
        btnTentarNovamente!!.setOnClickListener(View.OnClickListener { v: View? -> checkInternetAndLoadData() })
    }

    private fun checkInternetAndLoadData() {
        if (hasInternetConnection()) {
            if (loadCache()) {
                showContentState()
                setupCarousel()
                setupNews()
                fetchDataInBackground()
            } else {
                showLoadingState()
                fetchDataInBackground()
            }
        } else {
            showOfflineState()
        }
    }

    private fun fetchDataInBackground() {
        executor.execute(Runnable {
            try {
                val doc = fetchHomePageData()
                if (isValidSession(doc)) {
                    processPageContent(doc)
                    saveCache()
                    handler.postDelayed(Runnable { this.updateUIWithNewData() }, 5000)
                } else {
                    clearCache()
                    handler.post(Runnable { this.handleInvalidSession() })
                }
            } catch (e: IOException) {
                handler.post(Runnable { handleDataFetchError(e) })
            }
        })
    }

    @SuppressLint("NotifyDataSetChanged", "UseKtx")
    private fun updateUIWithNewData() {
        if (isFragmentDestroyed) return

        if (viewPager!!.adapter != null) {
            viewPager!!.adapter!!.notifyDataSetChanged()
        }
        if (newsRecyclerView!!.adapter != null) {
            newsRecyclerView!!.adapter!!.notifyDataSetChanged()
        }

        if (loadingContainer!!.isVisible) {
            showContentState()
            setupCarousel()
            setupNews()
        }
    }

    @Throws(IOException::class)
    private fun fetchHomePageData(): Document {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(HOME_URL)

        return Jsoup.connect(HOME_URL)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
            .header("Cookie", cookies ?: "")
            .timeout(10000)
            .get()
    }

    private fun isValidSession(doc: Document): Boolean {
        return doc.getElementById("home_banners_carousel") != null &&
                doc.selectFirst("div.col-12.col-lg-8.mb-5") != null
    }

    private fun processPageContent(doc: Document?) {
        val newCarousel: MutableList<CarouselItem> = ArrayList<CarouselItem>()
        val newNews: MutableList<NewsItem> = ArrayList<NewsItem>()

        processCarousel(doc, newCarousel)
        processNews(doc, newNews)

        carouselItems.clear()
        carouselItems.addAll(newCarousel)

        newsItems.clear()
        newsItems.addAll(newNews)
    }

    private fun handleInvalidSession() {
        if (isFragmentDestroyed) return
        navigateToWebView(OUT_URL)
        shouldReloadOnResume = true
    }

    private fun handleDataFetchError(e: IOException) {
        if (isFragmentDestroyed) return
        Log.e("HomeFragment", "Erro ao buscar dados: " + e.message)

        if (loadingContainer!!.isVisible &&
            carouselItems.isEmpty() && newsItems.isEmpty()
        ) {
            navigateToWebView(OUT_URL)
            shouldReloadOnResume = true
        }
    }

    private fun saveCache() {
        if (isFragmentDestroyed) return
        val context = getContext()
        if (context == null) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            val gson = Gson()

            val carouselJson = gson.toJson(carouselItems)
            val newsJson = gson.toJson(newsItems)

            putString(KEY_CAROUSEL_ITEMS, carouselJson)
            putString(KEY_NEWS_ITEMS, newsJson)
        }
    }

    private fun loadCache(): Boolean {
        if (isFragmentDestroyed) return false
        val context = getContext()
        if (context == null) return false

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()

        val carouselJson = prefs.getString(KEY_CAROUSEL_ITEMS, null)
        val newsJson = prefs.getString(KEY_NEWS_ITEMS, null)

        if (carouselJson != null && newsJson != null) {
            val carouselType = object : TypeToken<ArrayList<CarouselItem?>?>() {}.type
            val newsType = object : TypeToken<ArrayList<NewsItem?>?>() {}.type

            val cachedCarousel =
                gson.fromJson<MutableList<CarouselItem>?>(carouselJson, carouselType)
            val cachedNews = gson.fromJson<MutableList<NewsItem>?>(newsJson, newsType)

            if (cachedCarousel != null && cachedNews != null && !cachedCarousel.isEmpty() && !cachedNews.isEmpty()) {
                carouselItems.clear()
                carouselItems.addAll(cachedCarousel)

                newsItems.clear()
                newsItems.addAll(cachedNews)

                return true
            }
        }
        return false
    }

    private fun clearCache() {
        if (isFragmentDestroyed) return
        val context = getContext()
        if (context == null) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            remove(KEY_CAROUSEL_ITEMS)
                .remove(KEY_NEWS_ITEMS)
        }
    }

    private fun processCarousel(doc: Document?, carouselList: MutableList<CarouselItem>) {
        if (doc == null) return

        val carousel = doc.getElementById("home_banners_carousel")
        if (carousel == null) return

        val items = carousel.select(".carousel-item")
        for (item in items) {
            if (isFragmentDestroyed) return

            val linkElem = item.selectFirst("a")
            val linkUrl = if (linkElem != null) linkElem.attr("href") else ""
            var imgUrl = item.select("img").attr("src")

            if (!imgUrl.startsWith("http")) {
                imgUrl = "https://www.colegioetapa.com.br$imgUrl"
            }

            carouselList.add(CarouselItem(imgUrl, linkUrl))
        }
    }

    private fun processNews(doc: Document?, newsList: MutableList<NewsItem>) {
        if (doc == null) return

        val newsSection = doc.selectFirst("div.col-12.col-lg-8.mb-5")
        if (newsSection == null) return

        val cards = newsSection.select(".card.border-radius-card")
        cards.removeAll(newsSection.select("#modal-avisos-importantes .card.border-radius-card"))

        for (card in cards) {
            var iconUrl = card.select("img.aviso-icon").attr("src")
            val title = card.select("p.text-blue.aviso-text").text()
            val desc = card.select("p.m-0.aviso-text").text()
            val link = card.select("a[target=_blank]").attr("href")

            if (!iconUrl.startsWith("http")) {
                iconUrl = "https://areaexclusiva.colegioetapa.com.br$iconUrl"
            }

            if (!isDuplicateNews(title, newsList)) {
                newsList.add(NewsItem(iconUrl, title, desc, link))
            }
        }
    }

    private fun isDuplicateNews(title: String?, newsList: MutableList<NewsItem>): Boolean {
        for (ni in newsList) {
            if (ni.title == title) {
                return true
            }
        }
        return false
    }

    private fun navigateToWebView(url: String) {
        if (shouldBlockNavigation || isFragmentDestroyed) return

        try {
            val fragment = WebViewFragment().apply {
                arguments = createArgs(url)
            }
            requireActivity().supportFragmentManager
                .beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.nav_host_fragment, fragment)
                .addToBackStack(null)
                .commitAllowingStateLoss()
        } catch (e: IllegalStateException) {
            Log.e("HomeFragment", "Navigation blocked: ${e.message}")
        }
    }

    private fun showLoadingState() {
        if (isFragmentDestroyed) return
        handler.post(Runnable {
            loadingContainer!!.visibility = View.VISIBLE
            contentContainer!!.visibility = View.GONE
            layoutSemInternet!!.visibility = View.GONE
            txtStuckHint!!.visibility = View.VISIBLE
        })
    }

    private fun showContentState() {
        if (isFragmentDestroyed) return
        handler.post(Runnable {
            loadingContainer!!.visibility = View.GONE
            contentContainer!!.visibility = View.VISIBLE
            layoutSemInternet!!.visibility = View.GONE
            txtStuckHint!!.visibility = View.GONE
        })
    }

    private fun showOfflineState() {
        if (isFragmentDestroyed) return
        handler.post(Runnable {
            loadingContainer!!.visibility = View.GONE
            contentContainer!!.visibility = View.GONE
            layoutSemInternet!!.visibility = View.VISIBLE
            txtStuckHint!!.visibility = View.GONE
        })
    }

    private fun hasInternetConnection(): Boolean {
        if (isFragmentDestroyed) return false
        val context = getContext()
        if (context == null) return false

        val cm = context
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        if (cm == null) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork
            if (network == null) return false
            val caps = cm.getNetworkCapabilities(network)
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            val netInfo = cm.activeNetworkInfo
            return netInfo != null && netInfo.isConnected
        }
    }

    private fun setupCarousel() {
        // Configura o adapter primeiro
        viewPager!!.setAdapter(CarouselAdapter())
        viewPager!!.clipToPadding = false
        viewPager!!.setClipChildren(false)
        viewPager!!.setOffscreenPageLimit(3)

        // Ajusta a altura após a view ser medida
        viewPager!!.post(Runnable { this.adjustCarouselHeight() })
    }

    private fun adjustCarouselHeight() {
        if (isFragmentDestroyed || viewPager == null) return

        // Obtém a largura atual do ViewPager2
        val viewPagerWidth = viewPager!!.width

        // Se a largura ainda não estiver disponível, tenta novamente
        if (viewPagerWidth <= 0) {
            viewPager!!.post(Runnable { this.adjustCarouselHeight() })
            return
        }

        // Proporção 800:300 (300/800 = 0.375)
        val calculatedHeight = (viewPagerWidth * 0.375f).toInt()

        // Aplica limites mínimos e máximos
        val minHeight = resources.getDimension(R.dimen.carousel_min_height).toInt()
        val maxHeight = resources.getDimension(R.dimen.carousel_max_height).toInt()

        val finalHeight = max(
            minHeight.toDouble(),
            min(calculatedHeight.toDouble(), maxHeight.toDouble())
        ).toInt()

        // Aplica a altura
        val params = viewPager!!.layoutParams
        if (params.height != finalHeight) {
            params.height = finalHeight
            viewPager!!.setLayoutParams(params)
        }
    }

    private fun setupNews() {
        newsRecyclerView!!.setAdapter(NewsAdapter())
    }

    private inner class CarouselAdapter : RecyclerView.Adapter<CarouselViewHolder?>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarouselViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_carousel, parent, false)
            return CarouselViewHolder(v)
        }

        override fun onBindViewHolder(holder: CarouselViewHolder, position: Int) {
            val item = carouselItems[position]
            Glide.with(holder.itemView.context)
                .load(item.imageUrl)
                .centerCrop()
                .into(holder.imageView)
            holder.itemView.setOnClickListener(View.OnClickListener { v: View? ->
                navigateToWebView(
                    item.linkUrl!!
                )
            }
            )
        }

        override fun getItemCount(): Int {
            return carouselItems.size
        }
    }

    private inner class NewsAdapter : RecyclerView.Adapter<NewsViewHolder?>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.news_item, parent, false)
            return NewsViewHolder(v)
        }

        override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
            val item = newsItems[position]
            Glide.with(holder.itemView.context)
                .load(item.iconUrl)
                .into(holder.icon)
            holder.title.text = item.title
            holder.description.text = item.description
            holder.itemView.setOnClickListener(View.OnClickListener { v: View? ->
                navigateToWebView(
                    item.link!!
                )
            }
            )
        }

        override fun getItemCount(): Int {
            return newsItems.size
        }
    }

    internal class CarouselViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var imageView: ImageView = itemView.findViewById<ImageView>(R.id.imageView)
    }

    internal class NewsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var icon: ImageView = view.findViewById<ImageView>(R.id.news_icon)
        var title: TextView
        var description: TextView

        init {
            title = view.findViewById<TextView>(R.id.news_title)
            description = view.findViewById<TextView>(R.id.news_description)
        }
    }

    internal class CarouselItem {
        var imageUrl: String? = null
            private set
        var linkUrl: String? = null
            private set

        constructor(imageUrl: String?, linkUrl: String?) {
            this.imageUrl = imageUrl
            this.linkUrl = linkUrl
        }
    }

    internal class NewsItem {
        var iconUrl: String? = null
            private set
        var title: String? = null
            private set
        var description: String? = null
            private set
        var link: String? = null
            private set

        constructor(
            iconUrl: String?, title: String?,
            description: String?, link: String?
        ) {
            this.iconUrl = iconUrl
            this.title = title
            this.description = description
            this.link = link
        }
    }

    companion object {
        private const val PREFS_NAME = "HomeFragmentCache"
        private const val KEY_CAROUSEL_ITEMS = "carousel_items"
        private const val KEY_NEWS_ITEMS = "news_items"

        private const val HOME_URL = "https://areaexclusiva.colegioetapa.com.br/home"
        private const val OUT_URL = "https://areaexclusiva.colegioetapa.com.br"
    }
}