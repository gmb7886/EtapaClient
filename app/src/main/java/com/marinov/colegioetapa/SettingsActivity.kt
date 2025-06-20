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
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import com.marinov.colegioetapa.ui.theme.EtapaClientTheme
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

class SettingsActivity : ComponentActivity() {
    private val tag = "SettingsActivity"
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressBar: ProgressBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        configureSystemBarsForLegacyDevices()
        super.onCreate(savedInstanceState)

        setContent {
            EtapaClientTheme {
                SettingsScreen(
                    onNavigateUp = { finish() },
                    onCheckUpdate = { checkUpdate() },
                    onClearData = {
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        clearAllCacheData()
                        Toast.makeText(this, "Base de dados apagada com sucesso!", Toast.LENGTH_SHORT).show()
                    },
                    onClearPassword = {
                        clearAutoFill()
                        Toast.makeText(
                            this,
                            "Dados de preenchimento automático apagados com sucesso!",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onOpenUrl = { url -> openUrl(url) }
                )
            }
        }

        if (intent.getBooleanExtra("open_update_directly", false)) {
            checkUpdate()
        }
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
                            ContextCompat.getColor(this@SettingsActivity, R.color.fundocartao)
                        } else {
                            ContextCompat.getColor(this@SettingsActivity, R.color.fundocartao)
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
            val current = BuildConfig.VERSION_NAME

            if (UpdateChecker.isVersionGreater(latest, current)) {
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
            } else {
                showMessage()
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
                if (!apkFile.exists()) {
                    showError("Arquivo APK não encontrado")
                    return@runOnUiThread
                }

                val apkUri = FileProvider.getUriForFile(
                    this@SettingsActivity,
                    "${BuildConfig.APPLICATION_ID}.provider",
                    apkFile
                )

                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

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

// Composable para a tela de configurações
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    onCheckUpdate: () -> Unit,
    onClearData: () -> Unit,
    onClearPassword: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorResource(id = R.color.fundocartao)
                )
            )
        },
        containerColor = colorResource(id = R.color.fundocartao)
    ) { paddingValues ->

        if (isTablet) {
            // Layout para tablet - centralizado
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.TopCenter
            ) {
                SettingsContent(
                    modifier = Modifier
                        .width(600.dp) // Largura máxima para tablet
                        .fillMaxHeight(),
                    onCheckUpdate = onCheckUpdate,
                    onClearData = onClearData,
                    onClearPassword = onClearPassword,
                    onOpenUrl = onOpenUrl
                )
            }
        } else {
            // Layout para celular - largura total
            SettingsContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onCheckUpdate = onCheckUpdate,
                onClearData = onClearData,
                onClearPassword = onClearPassword,
                onOpenUrl = onOpenUrl
            )
        }
    }
}

@Composable
fun SettingsContent(
    modifier: Modifier = Modifier,
    onCheckUpdate: () -> Unit,
    onClearData: () -> Unit,
    onClearPassword: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Seção: Atualizações (mantém ícone padrão)
        SettingsSection(title = "Atualizações") {
            SettingsCard {
                SettingsButton(
                    text = "Verificar atualizações do app",
                    icon = Icons.Default.SystemUpdate,
                    onClick = onCheckUpdate
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Seção: Dados (mantém ícone padrão)
        SettingsSection(title = "Dados") {
            SettingsCard {
                SettingsButton(
                    text = "Limpar dados",
                    icon = Icons.Default.DeleteSweep,
                    onClick = onClearData
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Seção: Segurança (mantém ícone padrão)
        SettingsSection(title = "Segurança") {
            SettingsCard {
                SettingsButton(
                    text = "Limpar senhas e credenciais salvas",
                    icon = Icons.Default.Security,
                    onClick = onClearPassword
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Seção: Redes Sociais (usa ícones personalizados)
        SettingsSection(title = "Me acompanhe nas redes sociais:") {
            SocialCard {
                SocialButton(
                    text = "Twitter (@gmb7886)",
                    iconRes = R.drawable.ic_twitter,
                    onClick = { onOpenUrl("http://x.com/gmb7886") }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            SocialCard {
                SocialButton(
                    text = "Reddit (u/GMB7886)",
                    iconRes = R.drawable.ic_reddit,
                    onClick = { onOpenUrl("https://www.reddit.com/user/GMB7886/") }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            SocialCard {
                SocialButton(
                    text = "GitHub (gmb7886)",
                    iconRes = R.drawable.ic_github,
                    onClick = { onOpenUrl("https://github.com/gmb7886/") }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            SocialCard {
                SocialButton(
                    text = "YouTube (@CanalDoMarinov)",
                    iconRes = R.drawable.ic_youtube,
                    onClick = { onOpenUrl("https://youtube.com/@CanalDoMarinov") }
                )
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    )
    content()
}

@Composable
fun SettingsCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(id = R.color.fundocartao)
        ),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.padding(8.dp)) {
            content()
        }
    }
}

// Versão alternativa para redes sociais com ícones personalizados
@Composable
fun SocialCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(id = R.color.fundocartao)
        ),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.padding(8.dp)) {
            content()
        }
    }
}

@Composable
fun SettingsButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// Nova função para botões com ícones personalizados
@Composable
fun SocialButton(
    text: String,
    @androidx.annotation.DrawableRes iconRes: Int,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}