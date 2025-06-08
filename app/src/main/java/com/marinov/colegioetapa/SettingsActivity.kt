package com.marinov.colegioetapa

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.content.edit
import androidx.core.net.toUri

class SettingsActivity : AppCompatActivity() {
    private val tag = "SettingsActivity"
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var progressBar: ProgressBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        configureSystemBarsForLegacyDevices()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setupToolbar()
        setupUI()
        if (intent.getBooleanExtra("open_update_directly", false)) {
            checkUpdate()
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setupToolbarInsets()
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
                            ContextCompat.getColor(this@SettingsActivity, R.color.fundocartao)
                        } else {
                            ContextCompat.getColor(this@SettingsActivity, R.color.fundocartao)
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
    private fun setupToolbarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.toolbar)) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(
                v.paddingLeft,
                statusBarHeight,
                v.paddingRight,
                v.paddingBottom
            )
            insets
        }
    }

    private fun setupUI() {
        val btnCheck = findViewById<Button>(R.id.btn_check_update)
        val btnClear = findViewById<Button>(R.id.btn_clear_data)
        val btnClearPassword = findViewById<Button>(R.id.btn_clear_password)
        val btnTwitter = findViewById<Button>(R.id.btn_twitter)
        val btnReddit = findViewById<Button>(R.id.btn_reddit)
        val btnGithub = findViewById<Button>(R.id.btn_github)
        val btnYoutube = findViewById<Button>(R.id.btn_youtube)

        btnTwitter.setOnClickListener { openUrl("http://x.com/gmb7886") }
        btnReddit.setOnClickListener { openUrl("https://www.reddit.com/user/GMB7886/") }
        btnGithub.setOnClickListener { openUrl("https://github.com/gmb7886/") }
        btnYoutube.setOnClickListener { openUrl("https://youtube.com/@CanalDoMarinov") }
        btnCheck.setOnClickListener { checkUpdate()
        }

        btnClear.setOnClickListener {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            clearAllCacheData()
            Toast.makeText(this, "Base de dados apagada com sucesso!", Toast.LENGTH_SHORT).show()
        }
        btnClearPassword.setOnClickListener {
            clearAutoFill()
            Toast.makeText(
                this,
                "Dados de preenchimento automático apagados com sucesso!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun clearAllCacheData() {
        listOf(
            "horarios_prefs",
            "calendario_prefs",
            "materia_cache",
            "notas_prefs",
            "HomeFragmentCache",
            "provas_prefs",
            "redacao_detalhes_prefs",
            "cache_html_redacao_detalhes",
            "redacoes_prefs",
            "cache_html_redacoes",
            "material_prefs",
            "cache_html_material",
            "KEY_FILTRO",
            "graficos_prefs",
            "cache_html_graficos",
            "boletim_prefs",
            "cache_html_boletim",
            "redacao_semanal_prefs",
            "cache_html_redacao_semanal",
            "detalhes_prefs",
            "cache_html_detalhes",
            "cache_html_provas"
        ).forEach { clearSharedPreferences(it) }
    }

    private fun clearAutoFill() {
        clearSharedPreferences("autofill_prefs")
    }

    private fun clearSharedPreferences(name: String) {
        getSharedPreferences(name, MODE_PRIVATE).edit { clear() }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: Exception) {
            Log.e(tag, "Erro ao abrir URL", e)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun checkUpdate() = coroutineScope.launch {
        try {
            val (json, responseCode) = withContext(Dispatchers.IO) {
                val url = URL("https://api.github.com/repos/gmb7886/EtapaClient/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.setRequestProperty("User-Agent", "EtapaClient-Android")
                connection.connectTimeout = 10000
                connection.connect()

                try {
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        connection.inputStream.use { input ->
                            JSONObject(input.readText()) to connection.responseCode
                        }
                    } else {
                        null to connection.responseCode
                    }
                } finally {
                    connection.disconnect()
                }
            }

            if (json != null) {
                processReleaseData(json)
            } else {
                showError("Erro na conexão: Código $responseCode")
            }
        } catch (e: Exception) {
            Log.e(tag, "Erro na verificação", e)
            showError("Erro: ${e.message}")
        }
    }

    private fun InputStream.readText(): String {
        return BufferedReader(InputStreamReader(this)).use { it.readText() }
    }

    private fun processReleaseData(release: JSONObject) {
        runOnUiThread {
            val latest = release.getString("tag_name")
            if (latest == BuildConfig.VERSION_NAME) {
                showMessage()
            } else {
                val assets = release.getJSONArray("assets")
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }
                apkUrl?.let { promptForUpdate(it) } ?: showError("Arquivo APK não encontrado no release.")
            }
        }
    }

    private fun promptForUpdate(url: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Atualização Disponível")
                .setMessage("Deseja baixar e instalar a versão mais recente?")
                .setPositiveButton("Sim") { _, _ -> startManualDownload(url) }
                .setNegativeButton("Não", null)
                .show()
        }
    }

    private fun startManualDownload(apkUrl: String) {
        coroutineScope.launch {
            val progressDialog = createProgressDialog().apply { show() }
            try {
                val apkFile = withContext(Dispatchers.IO) { downloadApk(apkUrl) }
                progressDialog.dismiss()
                apkFile?.let(::showInstallDialog) ?: showError("Falha ao baixar o arquivo.")
            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e(tag, "Erro no download", e)
                showError("Falha no download: ${e.message}")
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun createProgressDialog(): AlertDialog {
        val view = layoutInflater.inflate(R.layout.dialog_download_progress, null)
        progressBar = view.findViewById(R.id.progress_bar)
        return AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()
    }

    private suspend fun downloadApk(apkUrl: String): File? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(apkUrl).openConnection() as HttpURLConnection
            connection.connect()

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val outputDir = File(downloadsDir, "EtapaClient").apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }

            val outputFile = File(outputDir, "app_release.apk")
            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    var total: Long = 0
                    val fileLength = connection.contentLength.toLong()

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        total += bytesRead
                        if (fileLength > 0) {
                            val progress = (total * 100 / fileLength).toInt()
                            withContext(Dispatchers.Main) {
                                progressBar?.progress = progress
                            }
                        }
                    }
                }
            }
            outputFile
        } catch (e: Exception) {
            Log.e(tag, "Erro no download", e)
            null
        }
    }

    private fun showInstallDialog(apkFile: File) {
        runOnUiThread {
            try {
                // Verifique se o arquivo existe
                if (!apkFile.exists()) {
                    showError("Arquivo APK não encontrado")
                    return@runOnUiThread
                }

                // Crie a URI usando FileProvider
                val apkUri = FileProvider.getUriForFile(
                    this@SettingsActivity,
                    "${BuildConfig.APPLICATION_ID}.provider",
                    apkFile
                )

                // Crie o intent de instalação
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                // Verifique se há um aplicativo para lidar com o intent
                if (installIntent.resolveActivity(packageManager) != null) {
                    AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("Download concluído")
                        .setMessage("Deseja instalar a atualização agora?")
                        .setPositiveButton("Instalar") { _, _ -> startActivity(installIntent) }
                        .setNegativeButton("Cancelar", null)
                        .show()
                } else {
                    showError("Nenhum aplicativo encontrado para instalar o APK")
                }
            } catch (e: Exception) {
                Log.e(tag, "Erro na instalação", e)
                showError("Erro ao iniciar a instalação: ${e.message}")
            }
        }
    }

    private fun showMessage() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setMessage("Você já está na versão mais recente")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun showError(msg: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Erro")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
        progressBar = null
    }
}