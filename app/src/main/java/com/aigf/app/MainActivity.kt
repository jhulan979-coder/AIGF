
package com.aigf.app

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var webView: WebView
    private lateinit var tts: TextToSpeech

    private val prefs by lazy {
        getSharedPreferences("aigf_settings", MODE_PRIVATE)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)
        webView = WebView(this)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        webView.addJavascriptInterface(SettingsBridge(), "AndroidSettings")
        webView.addJavascriptInterface(AIBridge(), "AndroidAI")
        webView.addJavascriptInterface(VoiceBridge(), "AndroidVoice")

        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        webView.destroy()
        super.onDestroy()
    }

    inner class SettingsBridge {
        @JavascriptInterface
        fun getKey(): String {
            return prefs.getString("gemini_key", "") ?: ""
        }

        @JavascriptInterface
        fun saveKey(key: String): Boolean {
            val cleaned = key.trim()
            if (cleaned.isEmpty()) return false

            return prefs.edit()
                .putString("gemini_key", cleaned)
                .commit()
        }

        @JavascriptInterface
        fun deleteKey() {
            prefs.edit().remove("gemini_key").apply()
        }
    }

    inner class AIBridge {
        @JavascriptInterface
        fun askGemini(message: String, language: String) {
            thread {
                var connection: HttpURLConnection? = null

                try {
                    val apiKey = prefs.getString("gemini_key", "") ?: ""

                    if (apiKey.isBlank()) {
                        sendToWeb("API key missing hai. Pehle Settings mein apni Gemini API key save karo.")
                        return@thread
                    }

                    val url = URL(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"
                    )

                    connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 20000
                    connection.readTimeout = 30000
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("x-goog-api-key", apiKey)
                    connection.doOutput = true

                    val languageInstruction = when (language) {
                        "hindi" -> "Reply in natural Hindi."
                        "english" -> "Reply in natural English."
                        else -> "Reply naturally in Hinglish."
                    }

                    val prompt = """
                        You are Aanya, a sweet, caring and friendly AI companion.
                        Be warm, natural, slightly playful and conversational.
                        $languageInstruction
                        Keep replies reasonably short.
                        User says: $message
                    """.trimIndent()

                    val body = JSONObject().apply {
                        put("contents", org.json.JSONArray().put(
                            JSONObject().put(
                                "parts",
                                org.json.JSONArray().put(
                                    JSONObject().put("text", prompt)
                                )
                            )
                        ))
                        put("generationConfig", JSONObject().apply {
                            put("temperature", 0.9)
                            put("maxOutputTokens", 500)
                        })
                    }

                    connection.outputStream.use {
                        it.write(body.toString().toByteArray(Charsets.UTF_8))
                    }

                    val code = connection.responseCode
                    val stream = if (code in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                    val response = stream?.bufferedReader()?.use {
                        it.readText()
                    } ?: ""

                    if (code !in 200..299) {
                        sendToWeb("Gemini error ($code): $response")
                        return@thread
                    }

                    val reply = JSONObject(response)
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")

                    sendToWeb(reply)

                } catch (e: Exception) {
                    sendToWeb("Connection nahi ho paya. Internet aur API key check karo.")
                } finally {
                    connection?.disconnect()
                }
            }
        }

        private fun sendToWeb(reply: String) {
            val safe = JSONObject.quote(reply)
            runOnUiThread {
                webView.evaluateJavascript(
                    "window.receiveGeminiReply($safe);",
                    null
                )
            }
        }
    }

    inner class VoiceBridge {
        @JavascriptInterface
        fun speak(text: String, language: String) {
            runOnUiThread {
                tts.language = if (language == "english") {
                    Locale.US
                } else {
                    Locale("hi", "IN")
                }
                tts.setSpeechRate(0.95f)
                tts.setPitch(1.05f)
                tts.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "AANYA_VOICE"
                )
            }
        }
    }
}
