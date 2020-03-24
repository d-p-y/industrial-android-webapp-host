package pl.todoit.IndustrialWebViewWithQr

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

class AndroidRequestScanQr(var host:WebViewFragment) {
    val json = Json(JsonConfiguration.Stable)

    @JavascriptInterface
    public fun requestScanQr(promiseId : String, label : String, regexpOrNull : String) {

        host.getApp()?.requestScanQr(
            ScanRequest(label, regexpOrNull),
            { x:String ->
                var replyJson = json.stringify(AndroidReply.serializer(), AndroidReply(promiseId, true, x))
                var msg = "androidPostReplyToPromise(\"" +
                    replyJson.replace("\"", "\\\"") +
                    "\")"
                host.getWebView()?.evaluateJavascript(msg, null)
            },
            {
                var replyJson = json.stringify(AndroidReply.serializer(), AndroidReply(promiseId, false, null))
                var msg = "androidPostReplyToPromise(\"" +
                    replyJson.replace("\"", "\\\"") +
                    "\")"
                host.getWebView()?.evaluateJavascript(msg, null)
            }
        )
    }
}

class WebViewFragment : Fragment() {

    fun getApp() : App? {
        var app = activity?.application

        if (app !is App) {
            return null
        }

        return app
    }

    fun getWebView() : WebView? {
        return activity?.findViewById(R.id.webView)
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var result = inflater.inflate(R.layout.fragment_web_view, container, false)

        var webView = result.findViewById<WebView>(R.id.webView)

        var app = activity?.application

        if (webView != null && app is App) {
            webView.settings.javaScriptEnabled = true
            webView.settings.loadsImagesAutomatically = true

            webView.addJavascriptInterface(AndroidRequestScanQr(this), "Android");

            webView.clearCache(true)
            webView.loadUrl("http://192.168.1.8:8888")
        }

        return result
    }
}
