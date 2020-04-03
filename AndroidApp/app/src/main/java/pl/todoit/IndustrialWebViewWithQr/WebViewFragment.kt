package pl.todoit.IndustrialWebViewWithQr

import kotlinx.coroutines.channels.Channel
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import timber.log.Timber

val json = Json(JsonConfiguration.Stable)

suspend fun postReply(host : WebViewFragment, reply : AndroidReply) {
    var replyJson = json.stringify(AndroidReply.serializer(), reply)
    var msg = "androidPostReplyToPromise(\"" +
            replyJson.replace("\"", "\\\"") +
            "\")"
    host.getWebView()?.evaluateJavascript(msg, null)
}

class AndroidRequestScanQr(var host:WebViewFragment) {

    @JavascriptInterface
    public fun requestScanQr(promiseId : String, label : String, regexpOrNull : String) {
        var act = host.activity

        if (act !is MainActivity) {
            Timber.e("no MainActivity")
            return
        }

        act.launchCoroutine(suspend {
            val req = ScanRequest(label, regexpOrNull)
            host.navigation.send(NavigationRequest.WebBrowser_RequestedScanQr(req))

            var maybeQr = req.scanResult.receive()
            postReply(host, AndroidReply(promiseId, maybeQr != null, maybeQr))
        })
    }
}

class WebViewFragment(val navigation:Channel<NavigationRequest>, val inp:ConnectionInfo) : Fragment() {

    fun getWebView() : WebView? = view?.findViewById(R.id.webView)

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

            Timber.d("navigating to ${inp.url}")
            webView.loadUrl(inp.url)
        }

        return result
    }
}
