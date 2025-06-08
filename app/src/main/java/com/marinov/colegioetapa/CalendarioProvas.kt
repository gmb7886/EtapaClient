package com.marinov.colegioetapa

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class CalendarioProvas : Fragment() {

    private companion object {
        const val URL_BASE = "https://areaexclusiva.colegioetapa.com.br/provas/datas"
        const val PREFS = "calendario_prefs"
        const val KEY_BASE = "cache_html_calendario_"
        const val KEY_SEM_PROVAS = "sem_provas_"
        const val KEY_FILTRO = "filtro_provas"
        const val FILTRO_TODOS = 0
        const val FILTRO_PROVAS = 1
        const val FILTRO_RECUPERACOES = 2
    }
    private lateinit var recyclerProvas: RecyclerView
    private lateinit var progressBar: CircularProgressIndicator
    private lateinit var barOffline: View
    private lateinit var txtSemProvas: TextView
    private lateinit var txtSemDados: TextView
    private lateinit var btnLogin: MaterialButton
    private lateinit var spinnerMes: Spinner
    private lateinit var btnFiltro: ImageButton
    private lateinit var adapter: ProvasAdapter
    private lateinit var cache: CacheHelper
    private var mesSelecionado: Int = 0
    private var filtroAtual: Int = FILTRO_TODOS
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_provas_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerProvas = view.findViewById(R.id.recyclerProvas)
        progressBar = view.findViewById(R.id.progress_circular)
        barOffline = view.findViewById(R.id.barOffline)
        txtSemProvas = view.findViewById(R.id.txt_sem_provas)
        txtSemDados = view.findViewById(R.id.txt_sem_dados)
        spinnerMes = view.findViewById(R.id.spinner_mes)
        btnLogin = view.findViewById(R.id.btnLogin)
        btnFiltro = view.findViewById(R.id.btnFiltro)

        setupRecyclerView()
        configurarSpinnerMeses()
        cache = CacheHelper(requireContext())

        // Configurar preferências e filtro
        prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        filtroAtual = prefs.getInt(KEY_FILTRO, FILTRO_TODOS)

        carregarDadosParaMes()

        btnLogin.setOnClickListener {
            (activity as? MainActivity)?.navigateToHome()
        }

        btnFiltro.setOnClickListener {
            mostrarMenuFiltro(it)
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    (activity as? MainActivity)?.navigateToHome()
                }
            }
        )
    }

    private fun setupRecyclerView() {
        recyclerProvas.layoutManager = LinearLayoutManager(requireContext())
        adapter = ProvasAdapter(emptyList(), this)
        recyclerProvas.adapter = adapter
    }

    private fun configurarSpinnerMeses() {
        val adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.meses_array,
            android.R.layout.simple_spinner_item
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        spinnerMes.adapter = adapter
        spinnerMes.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                mesSelecionado = position
                carregarDadosParaMes()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun mostrarMenuFiltro(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_filtro_provas, popup.menu)

        // Marcar o item atual
        when (filtroAtual) {
            FILTRO_TODOS -> popup.menu.findItem(R.id.filtro_todos).isChecked = true
            FILTRO_PROVAS -> popup.menu.findItem(R.id.filtro_provas).isChecked = true
            FILTRO_RECUPERACOES -> popup.menu.findItem(R.id.filtro_recuperacoes).isChecked = true
        }

        popup.setOnMenuItemClickListener { item ->
            filtroAtual = when (item.itemId) {
                R.id.filtro_todos -> FILTRO_TODOS
                R.id.filtro_provas -> FILTRO_PROVAS
                R.id.filtro_recuperacoes -> FILTRO_RECUPERACOES
                else -> return@setOnMenuItemClickListener false
            }

            // Salvar preferência e aplicar filtro
            prefs.edit { putInt(KEY_FILTRO, filtroAtual) }
            adapter.aplicarFiltro(filtroAtual)
            true
        }

        popup.show()
    }

    private fun carregarDadosParaMes() {
        if (!isOnline()) {
            exibirBarraOffline()
            verificarCache()
        } else {
            exibirCarregando()
            val url = if (mesSelecionado == 0) URL_BASE else "$URL_BASE?mes%5B%5D=$mesSelecionado"
            fetchProvas(url)
        }
    }

    private fun verificarCache() {
        when {
            cache.temProvas(mesSelecionado) -> carregarCacheProvas()
            cache.mesSemProvas(mesSelecionado) -> exibirMensagemSemProvas()
            else -> exibirSemDados()
        }
    }

    private fun exibirMensagemSemProvas() {
        recyclerProvas.visibility = View.GONE
        txtSemProvas.visibility = View.VISIBLE
        barOffline.visibility = View.GONE
        txtSemDados.visibility = View.GONE
    }

    private fun carregarCacheProvas() {
        cache.loadHtml(mesSelecionado)?.let { html ->
            val fake = Jsoup.parse(html)
            val table = fake.selectFirst("table")
            if (table != null) {
                parseAndDisplayTable(table)
                exibirConteudoOnline()
            }
        }
    }

    private fun fetchProvas(url: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val doc = withContext(Dispatchers.IO) {
                    try {
                        val cookieHeader = CookieManager.getInstance().getCookie(url)
                        Jsoup.connect(url)
                            .header("Cookie", cookieHeader)
                            .userAgent("Mozilla/5.0")
                            .timeout(15000)
                            .get()
                    } catch (e: Exception) {
                        Log.e("CalendarioProvas", "Erro na conexão", e)
                        null
                    }
                }

                progressBar.visibility = View.GONE

                when {
                    doc?.selectFirst("table") != null -> {
                        val table = doc.selectFirst("table")!!
                        cache.salvarProvas(table.outerHtml(), mesSelecionado)
                        parseAndDisplayTable(table)
                        exibirConteudoOnline()
                    }

                    doc?.selectFirst(".alert-info") != null -> {
                        cache.salvarMesSemProvas(mesSelecionado)
                        exibirMensagemSemProvas()
                    }

                    else -> {
                        verificarCache()
                        exibirBarraOffline()
                    }
                }
            } catch (_: Exception) {
                progressBar.visibility = View.GONE
                verificarCache()
                exibirBarraOffline()
            }
        }
    }

    private fun exibirCarregando() {
        progressBar.visibility = View.VISIBLE
        recyclerProvas.visibility = View.GONE
        txtSemProvas.visibility = View.GONE
        barOffline.visibility = View.GONE
        txtSemDados.visibility = View.GONE
    }

    private fun exibirConteudoOnline() {
        recyclerProvas.visibility = View.VISIBLE
        barOffline.visibility = View.GONE
        txtSemProvas.visibility = View.GONE
        txtSemDados.visibility = View.GONE
    }

    private fun exibirBarraOffline() {
        barOffline.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        txtSemProvas.visibility = View.GONE
        txtSemDados.visibility = View.GONE
    }

    private fun exibirSemDados() {
        recyclerProvas.visibility = View.GONE
        txtSemProvas.visibility = View.GONE
        barOffline.visibility = View.GONE
        txtSemDados.visibility = View.VISIBLE
    }

    private fun isOnline(): Boolean {
        return try {
            val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val netInfo: NetworkInfo? = cm.activeNetworkInfo
            netInfo != null && netInfo.isConnected
        } catch (_: Exception) {
            false
        }
    }

    private fun parseAndDisplayTable(table: Element) {
        val items = mutableListOf<ProvaItem>()
        val rows = table.select("tbody > tr")

        for (tr in rows) {
            val cells = tr.children()
            if (cells.size < 5) continue

            val data = cells[0].text()
            val codigo = cells[1].ownText()
            val linkElement = cells[1].selectFirst("a")
            val link = linkElement?.attr("href") ?: ""
            val tipo = cells[2].text()
            val conjunto = "${cells[3].text()}° conjunto"
            val materia = cells[4].text()

            if (data.isNotEmpty() && codigo.isNotEmpty()) {
                items.add(ProvaItem(data, codigo, link, tipo, conjunto, materia))
            }
        }

        adapter.setDadosOriginais(items)
        adapter.aplicarFiltro(filtroAtual)
    }

    private inner class ProvasAdapter(
        private var items: List<ProvaItem>,
        private val parentFragment: Fragment
    ) : RecyclerView.Adapter<ProvasAdapter.ViewHolder>() {

        private var dadosOriginais: List<ProvaItem> = items

        @SuppressLint("NotifyDataSetChanged")
        fun setDadosOriginais(dados: List<ProvaItem>) {
            dadosOriginais = dados
            aplicarFiltro(filtroAtual)
        }

        @SuppressLint("NotifyDataSetChanged")
        fun aplicarFiltro(filtro: Int) {
            items = when (filtro) {
                FILTRO_PROVAS -> dadosOriginais.filter { !it.tipo.contains("1ªrec", ignoreCase = true) }
                FILTRO_RECUPERACOES -> dadosOriginais.filter { it.tipo.contains("1ªrec", ignoreCase = true) }
                else -> dadosOriginais
            }
            notifyDataSetChanged()

            // Atualizar visibilidade da mensagem de sem provas
            if (items.isEmpty()) {
                txtSemProvas.visibility = View.VISIBLE
                recyclerProvas.visibility = View.GONE
                txtSemProvas.visibility = View.GONE
                recyclerProvas.visibility = View.VISIBLE
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_prova_calendar, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            holder.txtData.text = item.data
            holder.txtCodigo.text = item.codigo
            holder.txtConjunto.text = item.conjunto
            holder.txtMateria.text = item.materia

            // Identificador de tipo de prova
            val isRecuperacao = item.tipo.contains("1ªrec", ignoreCase = true)
            val badgeText = if (isRecuperacao) "1ªREC" else "PROVA"

            holder.badgeTipo.text = badgeText

            if (isRecuperacao) {
                // Estilo para recuperação
                holder.badgeContainer.setBackgroundResource(R.drawable.bg_prova_recuperacao)
                holder.badgeTipo.setTextColor(ContextCompat.getColor(holder.badgeTipo.context, R.color.badge_text_recuperacao))
            } else {
                // Estilo para prova normal
                holder.badgeContainer.setBackgroundResource(R.drawable.bg_prova_normal)
                holder.badgeTipo.setTextColor(ContextCompat.getColor(holder.badgeTipo.context, R.color.badge_text_normal))
            }

            holder.card.setOnClickListener {
                if (parentFragment.isAdded) {
                    val transaction = parentFragment.parentFragmentManager.beginTransaction()

                    // Verificar se é tablet
                    if (resources.getBoolean(R.bool.isTablet)) {
                        // Substituir apenas o conteúdo do painel direito
                        val currentDetail = parentFragment.parentFragmentManager
                            .findFragmentById(R.id.detail_container)

                        if (currentDetail != null) {
                            transaction.remove(currentDetail)
                        }

                        transaction.replace(
                            R.id.detail_container,
                            MateriadeProva.newInstance(item.link)
                        )
                    } else {
                        // Substituir o fragmento principal em dispositivos não tablets
                        transaction.replace(
                            R.id.nav_host_fragment,
                            MateriadeProva.newInstance(item.link)
                        )
                        transaction.addToBackStack(null)
                    }

                    transaction.commit()
                }
            }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val card: MaterialCardView = itemView.findViewById(R.id.card_prova)
            val txtData: TextView = itemView.findViewById(R.id.txt_data)
            val txtCodigo: TextView = itemView.findViewById(R.id.txt_codigo)
            val txtConjunto: TextView = itemView.findViewById(R.id.txt_conjunto)
            val txtMateria: TextView = itemView.findViewById(R.id.txt_materia)
            val badgeTipo: TextView = itemView.findViewById(R.id.badge_tipo)
            val badgeContainer: FrameLayout = itemView.findViewById(R.id.badge_tipo_container)
        }
    }

    private data class ProvaItem(
        val data: String,
        val codigo: String,
        val link: String,
        val tipo: String,
        val conjunto: String,
        val materia: String
    )

    private inner class CacheHelper(context: Context) {
        private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun salvarProvas(html: String, mes: Int) {
            prefs.edit {
                putString("$KEY_BASE$mes", html)
                remove("$KEY_SEM_PROVAS$mes")
            }
        }

        fun salvarMesSemProvas(mes: Int) {
            prefs.edit {
                putBoolean("$KEY_SEM_PROVAS$mes", true)
                remove("$KEY_BASE$mes")
            }
        }

        fun loadHtml(mes: Int): String? {
            return prefs.getString("$KEY_BASE$mes", null)
        }

        fun temProvas(mes: Int): Boolean {
            return prefs.contains("$KEY_BASE$mes")
        }

        fun mesSemProvas(mes: Int): Boolean {
            return prefs.getBoolean("$KEY_SEM_PROVAS$mes", false)
        }
    }
}