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
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
    }

    private var currentFragment: Fragment? = null
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var navRail: NavigationRailView

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

        // Configurar navegação para tablet/celular
        configureNavigationForDevice()

        // Se veio extra para abrir Notas diretamente
        val targetItem = intent.getIntExtra("EXTRA_NAV_ITEM_ID", -1)
        if (targetItem == R.id.navigation_notas) {
            if (resources.getBoolean(R.bool.isTablet)) {
                navRail.selectedItemId = targetItem
            } else {
                bottomNav.selectedItemId = targetItem
            }
        }

        if (savedInstanceState == null) {
            currentFragment = HomeFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.nav_host_fragment, currentFragment!!)
                .commit()
        }

        solicitarPermissaoNotificacao()
        iniciarNotasWorker()
        iniciarUpdateWorker()
    }

    private fun configureNavigationForDevice() {
        val isTablet = resources.getBoolean(R.bool.isTablet)

        if (isTablet) {
            // Modo tablet - usar Navigation Rail
            bottomNav.visibility = View.GONE
            navRail.visibility = View.VISIBLE
            navRail.setOnItemSelectedListener { handleNavigation(it) }
        } else {
            // Modo celular - usar Bottom Navigation
            navRail.visibility = View.GONE
            bottomNav.visibility = View.VISIBLE
            bottomNav.setOnItemSelectedListener { handleNavigation(it) }

            // Manter o código existente para ocultar ao abrir teclado
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val target = intent?.getIntExtra("EXTRA_NAV_ITEM_ID", -1) ?: -1
        if (target == R.id.navigation_notas) {
            if (resources.getBoolean(R.bool.isTablet)) {
                navRail.selectedItemId = target
            } else {
                bottomNav.selectedItemId = target
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
        // Aplicar apenas para Android 9 (Pie) e versões anteriores
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

                    // Android 7.1 ou inferior - Barras pretas sólidas
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                        statusBarColor = Color.BLACK
                        navigationBarColor = Color.BLACK

                        // NOVA IMPLEMENTAÇÃO: Forçar ícones brancos
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            var flags = decorView.systemUiVisibility
                            // Remove qualquer flag de ícones escuros
                            flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                            decorView.systemUiVisibility = flags
                        }
                    }
                    // Android 8.0-9.0: Mantém correções anteriores
                    else {
                        navigationBarColor = if (isDarkMode) {
                            ContextCompat.getColor(this@MainActivity, R.color.nav_bar_dark)
                        } else {
                            ContextCompat.getColor(this@MainActivity, R.color.nav_bar_light)
                        }
                    }
                }
            }

            // Controle de ícones para barra de status (Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                var flags = window.decorView.systemUiVisibility

                if (isDarkMode) {
                    // Tema escuro: remove flag de ícones escuros
                    flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                } else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                    // Tema claro apenas para versões superiores a Nougat
                    flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }

                window.decorView.systemUiVisibility = flags
            }

            // Controle de ícones para barra de navegação no tema claro (Android 8.0+)
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

    private fun handleNavigation(item: MenuItem): Boolean {
        val id = item.itemId
        var newFragment: Fragment? = null

        when (id) {
            R.id.navigation_home -> newFragment = HomeFragment()
            R.id.option_calendario_provas -> newFragment = CalendarioProvas()
            R.id.navigation_notas -> newFragment = NotasFragment()
            R.id.option_horarios_aula -> newFragment = HorariosAula()
            R.id.navigation_more -> {
                showMoreOptions()
                return false
            }
        }

        if (newFragment != null && newFragment != currentFragment) {
            currentFragment = newFragment
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.nav_host_fragment, newFragment)
                .commit()
            return true
        }
        return false
    }

    @SuppressLint("InflateParams")
    private fun showMoreOptions() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_more_options, null)

        // Configuração dos listeners
        view.findViewById<View>(R.id.navigation_provas).setOnClickListener {
            navigateToFragment(ProvasFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_digital).setOnClickListener {
            navigateToFragment(DigitalFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_link).setOnClickListener {
            navigateToFragment(LinkFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_acc_detalhes).setOnClickListener {
            navigateToFragment(AccFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_acc_inscricao).setOnClickListener {
            navigateToFragment(InscricaoAccFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_escreve_enviar).setOnClickListener {
            navigateToFragment(EscreveEnviarFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_escreve_ver).setOnClickListener {
            navigateToFragment(EscreveVerFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_redacao_semanal).setOnClickListener {
            navigateToFragment(RedacaoSemanalFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_boletim_simulados).setOnClickListener {
            navigateToFragment(BoletimSimuladosFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_graficos).setOnClickListener {
            navigateToFragment(GraficosFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_provas_gabaritos).setOnClickListener {
            navigateToFragment(ProvasGabaritos(), dialog)
        }
        view.findViewById<View>(R.id.option_detalhes_provas).setOnClickListener {
            navigateToFragment(DetalhesProvas(), dialog)
        }
        view.findViewById<View>(R.id.navigation_material).setOnClickListener {
            navigateToFragment(MaterialFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_plantao_duvidas).setOnClickListener {
            navigateToFragment(PlantaoDuvidas(), dialog)
        }
        view.findViewById<View>(R.id.option_food).setOnClickListener {
            navigateToFragment(CardapioFragment(), dialog)
        }
        view.findViewById<View>(R.id.option_plantao_duvidas_online).setOnClickListener {
            navigateToFragment(PlantaoDuvidasOnline(), dialog)
        }
        view.findViewById<View>(R.id.option_ead).setOnClickListener {
            navigateToFragment(EADFragment(), dialog)
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun navigateToFragment(fragment: Fragment, dialog: BottomSheetDialog) {
        currentFragment = fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
        dialog.dismiss()
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
                currentFragment = ProfileFragment()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment, currentFragment!!)
                    .commit()
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
            .replace(R.id.nav_host_fragment, fragment)
            .addToBackStack(null)
            .commit()
    }
    fun abrirDetalhesProva(url: String) {
        val fragment = DetalhesProvaFragment.newInstance(url)
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .addToBackStack(null)
            .commit()
    }
    fun navigateToHome() {
        if (resources.getBoolean(R.bool.isTablet)) {
            navRail.selectedItemId = R.id.navigation_home
        } else {
            bottomNav.selectedItemId = R.id.navigation_home
        }
    }
}