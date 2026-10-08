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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var webView: WebView
    private lateinit var textToSpeech: TextToSpeech

    // APNI GEMINI API KEY YAHAN PASTE KARO
    private val GEMINI_API_KEY = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        textToSpeech = TextToSpeech(this, this)

        webView = WebView(this)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        webView.addJavascriptInterface(
            AIBridge(),
            "AndroidAI"
        )

        webView.addJavascriptInterface(
            VoiceBridge(textToSpeech),
            "AndroidVoice"
        )

        webView.loadUrl("file:///android_asset/index.html")

        setContentView(webView)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale("hi", "IN")
        }
    }

    override fun onDestroy() {
        textToSpeech.stop()
        textToSpeech.shutdown()
        webView.destroy()
        super.onDestroy()
    }

    inner class AIBridge {

        @JavascriptInterface
        fun askGemini(message: String, language: String) {

            thread {

                try {

                    val url = URL(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"
                    )

                    val connection =
                        url.openConnection() as HttpURLConnection

                    connection.requestMethod = "POST"
                    connection.setRequestProperty(
                        "Content-Type",
                        "application/json"
                    )

                    connection.setRequestProperty(
                        "x-goog-api-key",
                        GEMINI_API_KEY
                    )

                    connection.doOutput = true

                    val languageInstruction =
                        when (language) {
                            "hindi" ->
                                "Reply in natural Hindi."

                            "english" ->
                                "Reply in natural English."

                            else ->
                                "Reply naturally in Hinglish, using Hindi and English mixed."
                        }

                    val prompt =
                        """
                        You are Aanya, a sweet, caring and friendly AI companion.

                        Personality:
                        - Warm
                        - Caring
                        - Slightly playful
                        - Natural
                        - Never robotic
                        - Talk like a real chat companion

                        $languageInstruction

                        Keep replies conversational and not unnecessarily long.

                        User says:
                        $message
                        """.trimIndent()

                    val json =
                        JSONObject().apply {

                            put(
                                "contents",
                                org.json.JSONArray().apply {

                                    put(
                                        JSONObject().apply {

                                            put(
                                                "parts",
                                                org.json.JSONArray().apply {

                                                    put(
                                                        JSONObject().apply {
                                                            put("text", prompt)
                                                        }
                                                    )

                                                }
                                            )

                                        }
                                    )

                                }
                            )

                            put(
                                "generationConfig",
                                JSONObject().apply {
                                    put("temperature", 0.9)
                                    put("maxOutputTokens", 500)
                                }
                            )
                        }

                    connection.outputStream.use { output ->
                        output.write(
                            json.toString().toByteArray()
                        )
                    }

                    val responseCode =
                        connection.responseCode

                    val stream =
                        if (responseCode in 200..299) {
                            connection.inputStream
                        } else {
                            connection.errorStream
                        }

                    val reader =
                        BufferedReader(
                            InputStreamReader(stream)
                        )

                    val response =
                        reader.readText()

                    reader.close()

                    if (responseCode !in 200..299) {

                        sendToWeb(
                            "Gemini error: $response"
                        )

                        return@thread
                    }

                    val result =
                        JSONObject(response)

                    val reply =
                        result
                            .getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")

                    sendToWeb(reply)

                } catch (e: Exception) {

                    sendToWeb(
                        "Sorry ❤️ Gemini se connection nahi ho paya."
                    )
                }
            }
        }

        private fun sendToWeb(reply: String) {

            val safeReply =
                JSONObject.quote(reply)

            runOnUiThread {

                webView.evaluateJavascript(
                    "window.receiveGeminiReply($safeReply);",
                    null
                )
            }
        }
    }
}

class VoiceBridge(
    private val tts: TextToSpeech
) {

    @JavascriptInterface
    fun speak(text: String, language: String) {

        val locale =
            if (language == "english") {
                Locale.US
            } else {
                Locale("hi", "IN")
            }

        tts.language = locale
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
