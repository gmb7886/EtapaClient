package com.marinov.colegioetapa

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ProvasWidget : AppWidgetProvider() {

    companion object {
        const val PREFS_WIDGET = "widget_provas_prefs"
        const val KEY_PROVAS = "provas_data"
        private const val TAG = "ProvasWidget"
        private val WEEK_IDS = listOf(
            R.id.week1, R.id.week2, R.id.week3, R.id.week4, R.id.week5, R.id.week6
        )

        fun updateWidget(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, ProvasWidget::class.java)
                val ids = appWidgetManager.getAppWidgetIds(componentName)

                for (appWidgetId in ids) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao atualizar widget", e)
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            try {
                val views = buildRemoteViews(context)
                val intent = Intent(context, MainActivity::class.java)
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    flags
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao construir widget", e)
                val errorViews = RemoteViews(context.packageName, R.layout.widget_error)
                errorViews.setTextViewText(R.id.widget_error_text, "Erro ao carregar")
                appWidgetManager.updateAppWidget(appWidgetId, errorViews)
            }
        }

        private fun buildRemoteViews(context: Context): RemoteViews {
            return try {
                val views = RemoteViews(context.packageName, R.layout.widget_calendar_provas)
                val calendar = Calendar.getInstance()
                val mesAno = SimpleDateFormat("MMMM yyyy", Locale("pt", "BR")).format(calendar.time)
                views.setTextViewText(R.id.txt_mes_ano, mesAno.replaceFirstChar { it.titlecase() })

                // Carregar dados
                val prefs = context.getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE)
                val provasData = prefs.getString(KEY_PROVAS, null)
                val provasMap = parseProvasData(provasData)
                val diaAtual = calendar.get(Calendar.DAY_OF_MONTH)

                // Construir cabeçalho
                val diasSemana = listOf("D", "S", "T", "Q", "Q", "S", "S")
                for ((index, dia) in diasSemana.withIndex()) {
                    val headerView = RemoteViews(context.packageName, R.layout.widget_dia_header)
                    headerView.setTextViewText(R.id.txt_dia_semana, dia)
                    views.addView(R.id.header_container, headerView)
                }

                // Construir calendário
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val primeiroDia = calendar.get(Calendar.DAY_OF_WEEK)
                val offset = (primeiroDia - Calendar.SUNDAY + 7) % 7
                var diaCounter = 1
                val totalDias = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

                for (weekId in WEEK_IDS) {
                    views.removeAllViews(weekId)
                    for (i in 0 until 7) {
                        val cellIndex = diaCounter - offset - 1
                        val isDiaValido = cellIndex >= 0 && diaCounter <= totalDias + offset

                        val cellView = if (isDiaValido) {
                            createDayCell(context, cellIndex + 1, diaAtual, provasMap)
                        } else {
                            RemoteViews(context.packageName, R.layout.widget_dia_vazio)
                        }
                        views.addView(weekId, cellView)
                        if (isDiaValido) diaCounter++
                    }
                }
                views
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao construir RemoteViews", e)
                RemoteViews(context.packageName, R.layout.widget_error).apply {
                    setTextViewText(R.id.widget_error_text, "Erro: ${e.localizedMessage}")
                }
            }
        }

        private fun createDayCell(
            context: Context,
            dia: Int,
            diaAtual: Int,
            provasMap: Map<Int, List<String>>
        ): RemoteViews {
            val cellView = RemoteViews(context.packageName, R.layout.widget_dia_item)
            cellView.setTextViewText(R.id.txt_dia, dia.toString())

            // Destacar dia atual
            if (dia == diaAtual) {
                cellView.setInt(R.id.dia_container, "setBackgroundResource", R.drawable.bg_dia_atual)
                cellView.setTextColor(R.id.txt_dia, ContextCompat.getColor(context, android.R.color.white))
            }

            // Adicionar provas
            provasMap[dia]?.let { codigos ->
                if (codigos.isNotEmpty()) {
                    val texto = codigos.take(3).joinToString(" ")
                    cellView.setTextViewText(R.id.txt_codigos, texto)
                    cellView.setViewVisibility(R.id.txt_codigos, View.VISIBLE)

                    val bgRes = if (codigos.any { it.contains("REC", ignoreCase = true) }) {
                        R.drawable.bg_prova_recuperacao
                    } else {
                        R.drawable.bg_prova_normal
                    }
                    cellView.setInt(R.id.dia_container, "setBackgroundResource", bgRes)
                }
            }
            return cellView
        }

        private fun parseProvasData(json: String?): Map<Int, List<String>> {
            val map = mutableMapOf<Int, MutableList<String>>()
            try {
                json?.let {
                    JSONArray(it).let { jsonArray ->
                        for (i in 0 until jsonArray.length()) {
                            val prova = jsonArray.getJSONObject(i)
                            val data = prova.getString("data")
                            val codigo = prova.getString("codigo")
                            data.split("/").firstOrNull()?.toIntOrNull()?.let { dia ->
                                map.getOrPut(dia) { mutableListOf() }.add(codigo)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao analisar JSON", e)
            }
            return map
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
}