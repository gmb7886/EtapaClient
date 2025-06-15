package com.marinov.colegioetapa

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.navigationrail.NavigationRailView
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 100
        private const val TAG = "MainActivity"
        private val mainMenuIds = setOf(
            R.id.navigation_home,
            R.id.option_calendario_provas,
            R.id.navigation_notas,
            R.id.option_horarios_aula
        )
    }

    private var currentFragment: Fragment? = null
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var navRail: NavigationRailView
    private var isLayoutReady = false
    private var currentFragmentId = View.NO_ID
    private var isUpdatingSelection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBarsForLegacyDevices()
        MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorPrimaryContainer,
            Color.BLACK
        )

        setContentView(R.layout.activity_main)

        val toolbar: MaterialToolbar = findViewById(R.id.topAppBar)
        setSupportActionBar(toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(
                v.paddingLeft,
                statusBarHeight,
                v.paddingRight,
                v.paddingBottom
            )
            insets
        }

        bottomNav = findViewById(R.id.bottom_navigation)
        navRail = findViewById(R.id.navigation_rail)

        // Aguardar layout estar pronto
        val rootView = findViewById<View>(R.id.main)
        rootView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (isLayoutReady) return

                // Remover listener após primeira execução
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                } else {
                    @Suppress("DEPRECATION")
                    rootView.viewTreeObserver.removeGlobalOnLayoutListener(this)
                }
                isLayoutReady = true

                // Configurar navegação após o layout estar pronto
                configureNavigationForDevice()

                // Processar intenção inicial
                handleIntent(intent)
            }
        })

        solicitarPermissaoNotificacao()
        iniciarNotasWorker()
        iniciarUpdateWorker()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Processar imediatamente se o layout estiver pronto
        if (isLayoutReady) {
            handleIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent?) {
        val destination = intent?.getStringExtra("destination") ?: run {
            // Se não há destino, e ainda não há fragmento, abrir a tela inicial
            if (currentFragment == null) {
                openFragment(R.id.navigation_home)
            }
            return
        }

        Log.d(TAG, "Handling intent with destination: $destination")

        when (destination) {
            "notas" -> openFragment(R.id.navigation_notas)
            "horarios" -> openFragment(R.id.option_horarios_aula)
            "provas" -> openFragment(R.id.option_calendario_provas)
        }
    }

    private fun openFragment(fragmentId: Int) {
        if (isFinishing || isDestroyed) return
        if (currentFragmentId == fragmentId) return  // Evitar reabrir o mesmo fragmento

        Log.d(TAG, "Opening fragment: $fragmentId")

        val fragment = when (fragmentId) {
            R.id.navigation_home -> HomeFragment()
            R.id.option_calendario_provas -> CalendarioProvas()
            R.id.navigation_notas -> NotasFragment()
            R.id.option_horarios_aula -> HorariosAula()
            R.id.action_profile -> ProfileFragment()
            else -> return
        }

        currentFragment = fragment
        currentFragmentId = fragmentId

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.nav_host_fragment, fragment)
            .commit()

        updateMenuSelection(fragmentId)
    }

    private fun updateMenuSelection(fragmentId: Int) {
        if (isUpdatingSelection) return

        Log.d(TAG, "Updating menu selection to: $fragmentId")

        isUpdatingSelection = true
        runOnUiThread {
            try {
                if (resources.getBoolean(R.bool.isTablet)) {
                    if (navRail.selectedItemId != fragmentId) {
                        navRail.selectedItemId = fragmentId
                    }
                } else {
                    if (bottomNav.selectedItemId != fragmentId) {
                        bottomNav.selectedItemId = fragmentId
                    }
                }
            } finally {
                isUpdatingSelection = false
            }
        }
    }

    private fun configureNavigationForDevice() {
        val isTablet = resources.getBoolean(R.bool.isTablet)

        if (isTablet) {
            bottomNav.visibility = View.GONE
            navRail.visibility = View.VISIBLE

            // Configurar o listener
            navRail.setOnItemSelectedListener { item ->
                if (isUpdatingSelection) return@setOnItemSelectedListener true

                if (item.itemId == R.id.navigation_more) {
                    showMoreOptions()
                    false
                } else {
                    openFragment(item.itemId)
                    true
                }
            }

            // Definir item inicial selecionado apenas visualmente
            if (currentFragmentId == View.NO_ID) {
                navRail.selectedItemId = R.id.navigation_home
            }
        } else {
            navRail.visibility = View.GONE
            bottomNav.visibility = View.VISIBLE

            bottomNav.setOnItemSelectedListener { item ->
                if (isUpdatingSelection) return@setOnItemSelectedListener true

                if (item.itemId == R.id.navigation_more) {
                    showMoreOptions()
                    false
                } else {
                    openFragment(item.itemId)
                    true
                }
            }

            // Definir item inicial selecionado apenas visualmente
            if (currentFragmentId == View.NO_ID) {
                bottomNav.selectedItemId = R.id.navigation_home
            }

            val rootView: View = findViewById(R.id.main)
            rootView.viewTreeObserver.addOnGlobalLayoutListener {
                val r = Rect()
                rootView.getWindowVisibleDisplayFrame(r)
                val screenHeight = rootView.rootView.height
                val keypadHeight = screenHeight - r.bottom
                bottomNav.visibility = if (keypadHeight > screenHeight * 0.15) View.GONE else View.VISIBLE
            }
        }
    }

    private fun iniciarUpdateWorker() {
        val updateWork = PeriodicWorkRequest.Builder(
            UpdateCheckWorker::class.java,
            15,
            TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "UpdateCheckWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            updateWork
        )
    }

    private fun configureSystemBarsForLegacyDevices() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val isDarkMode = when (AppCompatDelegate.getDefaultNightMode()) {
                AppCompatDelegate.MODE_NIGHT_YES -> true
                AppCompatDelegate.MODE_NIGHT_NO -> false
                else -> {
                    val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    currentNightMode == Configuration.UI_MODE_NIGHT_YES
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.apply {
                    clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                    addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                        statusBarColor = Color.BLACK
                        navigationBarColor = Color.BLACK

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            var flags = decorView.systemUiVisibility
                            flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                            decorView.systemUiVisibility = flags
                        }
                    } else {
                        navigationBarColor = if (isDarkMode) {
                            ContextCompat.getColor(this@MainActivity, R.color.nav_bar_dark)
                        } else {
                            ContextCompat.getColor(this@MainActivity, R.color.nav_bar_light)
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                var flags = window.decorView.systemUiVisibility

                if (isDarkMode) {
                    flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                } else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                    flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }

                window.decorView.systemUiVisibility = flags
            }

            if (!isDarkMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                var flags = window.decorView.systemUiVisibility
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                window.decorView.systemUiVisibility = flags
            }
        }
    }

    private fun solicitarPermissaoNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }
    }

    private fun iniciarNotasWorker() {
        val notasWork = PeriodicWorkRequest.Builder(
            NotasWorker::class.java,
            15,
            TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "NotasWorkerTask",
            ExistingPeriodicWorkPolicy.KEEP,
            notasWork
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @SuppressLint("InflateParams")
    private fun showMoreOptions() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_more_options, null)

        view.findViewById<View>(R.id.navigation_provas)?.setOnClickListener {
            openCustomFragment(ProvasFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_digital)?.setOnClickListener {
            openCustomFragment(DigitalFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_link)?.setOnClickListener {
            openCustomFragment(LinkFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_acc_detalhes)?.setOnClickListener {
            openCustomFragment(AccFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_acc_inscricao)?.setOnClickListener {
            openCustomFragment(InscricaoAccFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_escreve_enviar)?.setOnClickListener {
            openCustomFragment(EscreveEnviarFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_escreve_ver)?.setOnClickListener {
            openCustomFragment(EscreveVerFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_redacao_semanal)?.setOnClickListener {
            openCustomFragment(RedacaoSemanalFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_boletim_simulados)?.setOnClickListener {
            openCustomFragment(BoletimSimuladosFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_graficos)?.setOnClickListener {
            openCustomFragment(GraficosFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_provas_gabaritos)?.setOnClickListener {
            openCustomFragment(ProvasGabaritos(), dialog)
        }
        view.findViewById<View>(R.id.option_detalhes_provas)?.setOnClickListener {
            openCustomFragment(DetalhesProvas(), dialog)
        }
        view.findViewById<View>(R.id.navigation_material)?.setOnClickListener {
            openCustomFragment(MaterialFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_plantao_duvidas)?.setOnClickListener {
            openCustomFragment(PlantaoDuvidas(), dialog)
        }
        view.findViewById<View>(R.id.option_food)?.setOnClickListener {
            openCustomFragment(CardapioFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_plantao_duvidas_online)?.setOnClickListener {
            openCustomFragment(PlantaoDuvidasOnline(), dialog)
        }
        view.findViewById<View>(R.id.option_ead)?.setOnClickListener {
            openCustomFragment(EADFragment(), dialog)
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun openCustomFragment(fragment: Fragment, dialog: BottomSheetDialog) {
        currentFragment = fragment
        currentFragmentId = View.NO_ID

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
        dialog.dismiss()
        updateMenuSelection(View.NO_ID)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.top_app_bar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_profile -> {
                openFragment(R.id.action_profile)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun abrirWebView(url: String) {
        val fragment = WebViewFragment().apply {
            arguments = WebViewFragment.createArgs(url)
        }
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.nav_host_fragment, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun abrirDetalhesProva(url: String) {
        val fragment = DetalhesProvaFragment.newInstance(url)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.nav_host_fragment, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun navigateToHome() {
        openFragment(R.id.navigation_home)
    }
}