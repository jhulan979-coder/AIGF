package com.aigf.app

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var webView: WebView
    private lateinit var textToSpeech: TextToSpeech

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
}

class VoiceBridge(
    private val tts: TextToSpeech
) {

    @android.webkit.JavascriptInterface
    fun speak(text: String, language: String) {

        val locale = if (language == "english") {
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
