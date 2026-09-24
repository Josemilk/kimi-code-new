package com.josemilk.kimicode.android

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var transcript: TextView
    private lateinit var composer: EditText
    private lateinit var api: KimiApi
    private var language = "en"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = KimiApi(this)
        language = getPreferences(0).getString("language", Locale.getDefault().language)?.let { if (it == "es") "es" else "en" } ?: "en"
        buildUi()
    }

    private fun buildUi() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 18, 18, 12) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply { text = "Kimi Code"; textSize = 22f; setTypeface(typeface, 1) }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val settings = Button(this).apply { text = if (language == "es") "Ajustes" else "Settings"; setOnClickListener { settingsDialog() } }
        header.addView(settings)
        root.addView(header)

        val workspace = TextView(this).apply { text = if (language == "es") "  Workspace · Sesión nueva" else "  Workspace · New session"; textSize = 14f; setPadding(0, 18, 0, 18) }
        root.addView(workspace)
        transcript = TextView(this).apply { textSize = 15f; setPadding(8, 12, 8, 12); text = if (language == "es") "Describe una tarea para comenzar." else "Describe a task to get started." }
        root.addView(ScrollView(this).apply { addView(transcript) }, LinearLayout.LayoutParams(-1, 0, 1f))
        composer = EditText(this).apply { hint = if (language == "es") "Escribe una tarea…" else "Describe a task…"; minLines = 3; gravity = Gravity.TOP }
        root.addView(composer)
        val send = Button(this).apply { text = if (language == "es") "Enviar" else "Send"; setOnClickListener { sendTask() } }
        root.addView(send)
        setContentView(root)
    }

    private fun sendTask() {
        val text = composer.text.toString().trim(); if (text.isEmpty()) return
        if (!api.hasApiKey()) { apiKeyDialog(); return }
        transcript.text = "You: $text\n\nKimi: …"
        composer.setText("")
        api.chat(text, { answer -> transcript.text = "You: $text\n\nKimi:\n$answer" }, {}, { e -> transcript.text = "Error: $e" })
    }

    private fun apiKeyDialog() {
        val input = EditText(this); input.inputType = 129; input.hint = "Kimi API key"
        AlertDialog.Builder(this).setTitle(if (language == "es") "Conectar Kimi" else "Connect Kimi").setMessage(if (language == "es") "Introduce tu clave oficial de Kimi Code." else "Enter your official Kimi Code API key.").setView(input).setNegativeButton(if (language == "es") "Cancelar" else "Cancel", null).setPositiveButton("OK") { _, _ -> api.setApiKey(input.text.toString()) }.show()
    }

    private fun settingsDialog() {
        val options = arrayOf("English", "Español")
        AlertDialog.Builder(this).setTitle(if (language == "es") "Idioma" else "Language").setItems(options) { _, which ->
            language = if (which == 1) "es" else "en"; getPreferences(0).edit().putString("language", language).apply(); buildUi()
        }.show()
    }
}
