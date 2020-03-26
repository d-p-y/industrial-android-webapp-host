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
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

val json = Json(JsonConfiguration.Stable)

fun postReply(host : WebViewFragment, reply : AndroidReply) {
    var replyJson = json.stringify(AndroidReply.serializer(), reply)
    var msg = "androidPostReplyToPromise(\"" +
            replyJson.replace("\"", "\\\"") +
            "\")"
    host.getWebView()?.evaluateJavascript(msg, null)
}

class AndroidRequestScanQr(var host:WebViewFragment) {

    @JavascriptInterface
    public fun requestScanQr(promiseId : String, label : String, regexpOrNull : String) {
        host.getApp()?.requestScanQr(
            ScanRequest(label, regexpOrNull),
            { postReply(host, AndroidReply(promiseId, true, it)) },
            { postReply(host, AndroidReply(promiseId, false, null)) } )
    }
}

class WebViewFragment : Fragment() {
    var onPostCreate : ((x:WebView)->Unit)? = null

    fun getApp() : App? {
        var app = activity?.application

        if (app !is App) {
            return null
        }

        return app
    }

    fun setNavigation(inp:ConnectionInfo) {
        onPostCreate = {
            it.loadUrl(inp.url)
        }
    }

    fun getWebView() : WebView? {
        return view?.findViewById(R.id.webView)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var result = inflater.inflate(R.layout.fragment_web_view, container, false)

        var webView = result.findViewById<WebView>(R.id.webView)

        var app = activity?.application

        if (webView != null && app is App) {
            webView.settings.javaScriptEnabled = true
            webView.settings.loadsImagesAutomatically = true
            webView.setWebViewClient(WebViewClient()) //otherwise default browser app is open on URL change
            webView.addJavascriptInterface(AndroidRequestScanQr(this), "Android")
            webView.clearCache(true)

            onPostCreate?.invoke(webView)
        }

        return result
    }
}
