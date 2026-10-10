
package com.aigf.app

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.media.MediaPlayer
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var webView: WebView
    private lateinit var tts: TextToSpeech
    private var elevenPlayer: MediaPlayer? = null

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
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }

        elevenPlayer?.release()
        elevenPlayer = null

        if (::webView.isInitialized) {
            webView.destroy()
        }

        super.onDestroy()
    }

    inner class SettingsBridge {

        @JavascriptInterface
        fun getKey(): String =
            prefs.getString("gemini_key", "") ?: ""

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

        @JavascriptInterface
        fun saveElevenLabsKey(key: String): Boolean {
            val cleaned = key.trim()
            if (cleaned.isEmpty()) return false

            return prefs.edit()
                .putString("elevenlabs_key", cleaned)
                .commit()
        }

        @JavascriptInterface
        fun getElevenLabsKey(): String =
            prefs.getString("elevenlabs_key", "") ?: ""

        @JavascriptInterface
        fun saveElevenLabsVoiceId(voiceId: String): Boolean {
            val cleaned = voiceId.trim()
            if (cleaned.isEmpty()) return false

            return prefs.edit()
                .putString("elevenlabs_voice_id", cleaned)
                .commit()
        }

        @JavascriptInterface
        fun getElevenLabsVoiceId(): String =
            prefs.getString("elevenlabs_voice_id", "") ?: ""
    }

    inner class AIBridge {

        @JavascriptInterface
        fun askGemini(message: String, language: String) {
            thread {
                val apiKey =
                    prefs.getString("gemini_key", "") ?: ""

                if (apiKey.isBlank()) {
                    sendToWeb("API key missing hai. Settings mein save karo.")
                    return@thread
                }

                val languageInstruction = when (language) {
                    "hindi" -> "Reply in natural Hindi."
                    "english" -> "Reply in natural English."
                    else -> "Reply naturally in Hinglish."
                }

                val prompt = """
                    You are Aanya, a friendly and caring AI companion.
                    Be warm, natural, slightly playful and conversational.
                    $languageInstruction
                    Keep replies reasonably short.
                    User says: $message
                """.trimIndent()

                val models = listOf(
                    "gemini-3.8-flash",
                    "gemini-3.5-flash-lite"
                )

                var finalError = "Gemini abhi available nahi hai."

                try {
                    for (model in models) {
                        for (attempt in 0..2) {
                            val result = try {
                                requestModel(apiKey, model, prompt)
                            } catch (e: Exception) {
                                Pair(503, "")
                            }

                            val code = result.first
                            val body = result.second

                            if (code in 200..299) {
                                try {
                                    val json = JSONObject(body)
                                    val reply = json
                                        .getJSONArray("candidates")
                                        .getJSONObject(0)
                                        .getJSONObject("content")
                                        .getJSONArray("parts")
                                        .getJSONObject(0)
                                        .getString("text")

                                    sendToWeb(reply)
                                    return@thread
                                } catch (e: Exception) {
                                    finalError =
                                        "Gemini ka response samajh nahi aaya."
                                    break
                                }
                            }

                            finalError = "Gemini error ($code): $body"

                            if (code == 429 || code in 500..599) {
                                if (attempt < 2) {
                                    Thread.sleep(
                                        1000L * (1L shl attempt)
                                    )
                                    continue
                                }
                                break
                            }

                            if (code == 404) break

                            sendToWeb(finalError)
                            return@thread
                        }
                    }

                    sendToWeb(
                        "Dono Gemini models se jawab nahi mila. " +
                            "Thodi der baad try karo. $finalError"
                    )
                } catch (e: Exception) {
                    sendToWeb(
                        "Connection mein problem hui. Internet check karke dobara try karo."
                    )
                }
            }
        }

        private fun requestModel(
            apiKey: String,
            model: String,
            prompt: String
        ): Pair<Int, String> {
            var connection: HttpURLConnection? = null

            try {
                val url = URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
                )

                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 20000
                connection.readTimeout = 30000

                connection.setRequestProperty(
                    "Content-Type", "application/json"
                )
                connection.setRequestProperty(
                    "x-goog-api-key", apiKey
                )
                connection.doOutput = true

                val body = JSONObject().apply {
                    put(
                        "contents",
                        org.json.JSONArray().put(
                            JSONObject().put(
                                "parts",
                                org.json.JSONArray().put(
                                    JSONObject().put("text", prompt)
                                )
                            )
                        )
                    )
                    put(
                        "generationConfig",
                        JSONObject().apply {
                            put("temperature", 0.8)
                            put("maxOutputTokens", 500)
                        }
                    )
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

                val response = stream?.bufferedReader()
                    ?.use { it.readText() } ?: ""

                return Pair(code, response)
            } finally {
                connection?.disconnect()
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

        @JavascriptInterface
        fun speakElevenLabs(text: String) {
            thread {
                var connection: HttpURLConnection? = null
                var audioFile: File? = null

                try {
                    val apiKey =
                        prefs.getString("elevenlabs_key", "") ?: ""
                    val voiceId =
                        prefs.getString("elevenlabs_voice_id", "") ?: ""

                    if (apiKey.isBlank() || voiceId.isBlank()) {
                        return@thread
                    }

                    val url = URL(
                        "https://api.elevenlabs.io/v1/text-to-speech/$voiceId"
                    )

                    connection =
                        url.openConnection() as HttpURLConnection

                    connection.requestMethod = "POST"
                    connection.connectTimeout = 20000
                    connection.readTimeout = 60000
                    connection.setRequestProperty("xi-api-key", apiKey)
                    connection.setRequestProperty(
                        "Content-Type", "application/json"
                    )
                    connection.setRequestProperty(
                        "Accept", "audio/mpeg"
                    )
                    connection.doOutput = true

                    val body = JSONObject().apply {
                        put("text", text)
                        put("model_id", "eleven_multilingual_v2")
                        put("voice_settings", JSONObject().apply {
                            put("stability", 0.45)
                            put("similarity_boost", 0.8)
                        })
                    }

                    connection.outputStream.use {
                        it.write(
                            body.toString().toByteArray(Charsets.UTF_8)
                        )
                    }

                    
                 val responseCode = connection.responseCode

    if (responseCode !in 200..299) {
      android.util.Log.e(
        "AanyaVoice",
        "ElevenLabs request failed: HTTP $responseCode"
      )

    runOnUiThread {
        android.widget.Toast.makeText(
            this@MainActivity,
            "ElevenLabs error: HTTP $responseCode",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }

    return@thread
 }   


                    val file = File.createTempFile(
                        "aanya_voice_", ".mp3", cacheDir
                    )
                    audioFile = file

                    connection.inputStream.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    runOnUiThread {
                        var player: MediaPlayer? = null

                        try {
                            elevenPlayer?.release()
                            elevenPlayer = null

                            player = MediaPlayer()
                            val currentPlayer = player!!

                            elevenPlayer = currentPlayer

                            currentPlayer.setOnPreparedListener {
                                it.start()
                            }

                            currentPlayer.setOnCompletionListener {
                                it.release()

                                if (elevenPlayer === it) {
                                    elevenPlayer = null
                                }

                                file.delete()
                            }

                            currentPlayer.setOnErrorListener {
                                    mediaPlayer, _, _ ->
                                mediaPlayer.release()

                                if (elevenPlayer === mediaPlayer) {
                                    elevenPlayer = null
                                }

                                file.delete()
                                true
                            }

                            currentPlayer.setDataSource(file.absolutePath)
                            currentPlayer.prepareAsync()

                        } catch (e: Exception) {
                            player?.release()

                            if (elevenPlayer === player) {
                                elevenPlayer = null
                            }

                            file.delete()
                        }
                    }

                    audioFile = null

                } catch (e: Exception) {
                    audioFile?.delete()
                } finally {
                    connection?.disconnect()
                }
            }
        }
    }
}
