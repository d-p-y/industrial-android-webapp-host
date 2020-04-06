package pl.todoit.IndustrialWebViewWithQr.fragments

import kotlinx.coroutines.channels.Channel
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import pl.todoit.IndustrialWebViewWithQr.App
import pl.todoit.IndustrialWebViewWithQr.MainActivity
import pl.todoit.IndustrialWebViewWithQr.NavigationRequest
import pl.todoit.IndustrialWebViewWithQr.R
import pl.todoit.IndustrialWebViewWithQr.model.*
import timber.log.Timber

val json = Json(JsonConfiguration.Stable)

fun postReply(host : WebViewFragment, reply : AndroidReply) {
    var replyJson = json.stringify(AndroidReply.serializer(), reply)
    var msg =
        "androidPostReplyToPromise(\"" +
        replyJson.replace("\"", "\\\"") +
        "\")"
    host.getWebView()?.evaluateJavascript(msg, null)
}

class WebviewExposedMethods(var host: WebViewFragment) {

    @JavascriptInterface
    public fun setToolbarBackButtonState(state: Boolean) = host.onBackButtonStateChanged(state)

    @JavascriptInterface
    public fun setTitle(currentTitle: String) = host.onTitleChanged(currentTitle)

    @JavascriptInterface
    public fun showToast(text : String, durationLong: Boolean) =
        Toast.makeText(host.activity, text, if (durationLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

    @JavascriptInterface
    public fun requestScanQr(promiseId : String, label : String, regexpOrNull : String) {
        var act = host.activity

        if (act !is MainActivity) {
            Timber.e("no MainActivity")
            return
        }

        act.launchCoroutine(suspend {
            val req = ScanRequest(label, regexpOrNull)
            host.navigation.send(
                NavigationRequest.WebBrowser_RequestedScanQr(req)
            )

            var maybeQr = req.scanResult.receive()
            postReply(
                host,
                AndroidReply(promiseId,maybeQr != null, maybeQr)
            )
        })
    }
}

val trues = arrayOf("true")
val nulls = arrayOf(null, "null")

class WebViewFragment(val navigation:Channel<NavigationRequest>, val inp: ConnectionInfo) : Fragment(), IHasTitle, ITogglesBackButtonVisibility {

    private var _currentTitle : String = "Untitled WebApp"
    private var _backButtonEnabled = false

    fun getWebView() : WebView? = view?.findViewById(R.id.webView)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var result = inflater.inflate(R.layout.fragment_web_view, container, false)
        var webView = result.findViewById<WebView>(R.id.webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.loadsImagesAutomatically = true
        webView.setWebViewClient(WebViewClient()) //otherwise default browser app is open on URL change
        webView.addJavascriptInterface(WebviewExposedMethods(this), "Android")
        webView.clearCache(true)

        Timber.d("navigating to ${inp.url}")
        webView.loadUrl(inp.url)

        return result
    }

    //true if consumed
    suspend fun onBackPressedConsumed() : Boolean? {
        var act = activity

        if (act !is MainActivity) {
            Timber.e("not in MainActivity")
            return null
        }

        val result = Channel<Boolean?>()
        getWebView()?.evaluateJavascript("(window.androidBackConsumed === undefined) ? null : window.androidBackConsumed()", {
            act.launchCoroutine (suspend {
                if (it in nulls) {
                    result.send(null)
                } else {
                    result.send(it in trues)
                } })
        })

        return result.receive()
    }

    override fun isBackButtonEnabled(): Boolean = _backButtonEnabled

    fun onBackButtonStateChanged(enabled : Boolean) {
        _backButtonEnabled = enabled

        var act = activity

        if (act !is MainActivity) {
            Timber.e("not in MainActivity")
            return
        }

        act.launchCoroutine { navigation.send(
            NavigationRequest._ToolbarBackButtonStateChanged(this)
        ) }
    }

    override fun getTitle(): String = _currentTitle

    fun onTitleChanged(currentTitle: String) {
        _currentTitle = currentTitle

        var act = activity

        if (act !is MainActivity) {
            Timber.e("not in MainActivity")
            return
        }

        act.launchCoroutine { navigation.send(
            NavigationRequest._ToolbarTitleChanged(this)
        ) }
    }
}
