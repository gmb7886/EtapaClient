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
    }

    private var currentFragment: Fragment? = null
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var navRail: NavigationRailView
    private var isHandlingNavigation = false

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

        configureNavigationForDevice()

        if (savedInstanceState == null) {
            handleShortcutIntent(intent)
        }

        solicitarPermissaoNotificacao()
        iniciarNotasWorker()
        iniciarUpdateWorker()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun handleShortcutIntent(intent: Intent?) {
        val destination = intent?.getStringExtra("destination") ?: return
        Log.d(TAG, "Handling shortcut intent: $destination")

        when (destination) {
            "notas" -> navigateToFragment(R.id.navigation_notas)
            "horarios" -> navigateToFragment(R.id.option_horarios_aula)
            "provas" -> navigateToFragment(R.id.option_calendario_provas)
        }
    }

    private fun navigateToFragment(fragmentId: Int) {
        if (isHandlingNavigation) return

        isHandlingNavigation = true
        Log.d(TAG, "Navigating to fragment: $fragmentId")

        try {
            val fragment = when (fragmentId) {
                R.id.navigation_home -> HomeFragment()
                R.id.option_calendario_provas -> CalendarioProvas()
                R.id.navigation_notas -> NotasFragment()
                R.id.option_horarios_aula -> HorariosAula()
                R.id.action_profile -> ProfileFragment()
                else -> return
            }

            if (currentFragment?.javaClass != fragment.javaClass) {
                currentFragment = fragment
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.nav_host_fragment, fragment)
                    .commit()
            }

            updateMenuSelection(fragmentId)
        } finally {
            isHandlingNavigation = false
        }
    }

    private fun updateMenuSelection(fragmentId: Int) {
        if (isHandlingNavigation) return

        Log.d(TAG, "Updating menu selection to: $fragmentId")
        isHandlingNavigation = true

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
            isHandlingNavigation = false
        }
    }

    private fun configureNavigationForDevice() {
        val isTablet = resources.getBoolean(R.bool.isTablet)

        if (isTablet) {
            bottomNav.visibility = View.GONE
            navRail.visibility = View.VISIBLE
            navRail.setOnItemSelectedListener { item ->
                if (!isHandlingNavigation && item.itemId != R.id.navigation_more) {
                    navigateToFragment(item.itemId)
                }
                true
            }
        } else {
            navRail.visibility = View.GONE
            bottomNav.visibility = View.VISIBLE
            bottomNav.setOnItemSelectedListener { item ->
                if (!isHandlingNavigation && item.itemId != R.id.navigation_more) {
                    navigateToFragment(item.itemId)
                }
                true
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
                navigateToFragment(R.id.action_profile)
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
        navigateToFragment(R.id.navigation_home)
    }
}