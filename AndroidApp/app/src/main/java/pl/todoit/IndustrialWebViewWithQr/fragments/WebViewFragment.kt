package pl.todoit.IndustrialWebViewWithQr.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import kotlinx.coroutines.channels.Channel
import pl.todoit.IndustrialWebViewWithQr.App
import pl.todoit.IndustrialWebViewWithQr.NavigationRequest
import pl.todoit.IndustrialWebViewWithQr.R
import pl.todoit.IndustrialWebViewWithQr.model.*
import timber.log.Timber

fun postReply(host : WebViewFragment, reply : AndroidReply) {
    var replyJson = jsonStrict.stringify(AndroidReply.serializer(), reply)
    var msg =
        "androidPostReplyToPromise(\"" +
        replyJson.replace("\"", "\\\"") +
        "\")"
    host.getWebView()?.evaluateJavascript(msg, null)
}

typealias ScannedOrCancelled = Choice2<String,Unit>

class WebViewExposedMethods(private var host: WebViewFragment) {

    @JavascriptInterface
    fun setToolbarBackButtonState(state: Boolean) = host.onBackButtonStateChanged(state)

    @JavascriptInterface
    fun setTitle(currentTitle: String) = host.onTitleChanged(currentTitle)

    @JavascriptInterface
    fun showToast(text : String, durationLong: Boolean) =
        Toast.makeText(host.activity, text, if (durationLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

    @JavascriptInterface
    fun requestScanQr(promiseId : String, asksJsForValidation:Boolean, layoutStrategyAsJson : String) {
        App.Instance.launchCoroutine {
            val decoderReplyChannel = Channel<ScannedOrCancelled>()
            val req = ScanRequest(
                promiseId,
                if (!asksJsForValidation) PauseOrFinish.Finish else PauseOrFinish.Pause,
                decoderReplyChannel,
                deserializeLayoutStrategy(layoutStrategyAsJson))

            App.Instance.navigation.send(NavigationRequest.WebBrowser_RequestedScanQr(req))

            for (rawReply in decoderReplyChannel) {
                val reply =
                    when(rawReply) {
                        is Choice2.Choice1Of2 ->
                            AndroidReply(
                                PromiseId = promiseId,
                                IsCancellation = false,
                                Barcode = rawReply.value)

                        is Choice2.Choice2Of2 ->
                            AndroidReply(
                                PromiseId = promiseId,
                                IsCancellation = true,
                                Barcode = null)
                    }
                postReply(host, reply)
            }
            Timber.d("requestScanQr($promiseId) finished as reply channel is closed")
        }
    }

    @JavascriptInterface
    fun resumeScanQr(promiseId : String) {
        App.Instance.launchCoroutine {
            App.Instance.navigation.send(NavigationRequest.WebBrowser_ResumeScanQr(promiseId))
        }
    }

    @JavascriptInterface
    fun cancelScanQr(promiseId : String) {
        App.Instance.launchCoroutine {
            App.Instance.navigation.send(NavigationRequest.WebBrowser_CancelScanQr(promiseId))
        }
    }
}

val trues = arrayOf("true")

class WebViewFragment : Fragment(), IHasTitle, ITogglesBackButtonVisibility, IProcessesBackButtonEvents {
    private fun connInfo() = App.Instance.webViewFragmentParams.get()

    private var _currentTitle : String = "Untitled WebApp"
    private var _backButtonEnabled = false

    fun getWebView() : WebView? = view?.findViewById(R.id.webView)


    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var result = inflater.inflate(R.layout.fragment_web_view, container, false)
        var webView = result.findViewById<WebView>(R.id.webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.loadsImagesAutomatically = true
        webView.webViewClient = WebViewClient() //otherwise default browser app is open on URL change
        webView.addJavascriptInterface(WebViewExposedMethods(this), "Android")

        if (connInfo()?.forwardConsoleLogToLogCat == true) {
            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                    Timber.d("received console.log: ${msg?.sourceId()}:${msg?.lineNumber()} ${msg?.message()}")
                    return true
                }
            }
        }

        WebView.setWebContentsDebuggingEnabled(connInfo()?.remoteDebuggerEnabled == true)

        if (connInfo()?.forceReloadFromNet == true) {
            webView.clearCache(true)
        }

        Timber.d("navigating to ${connInfo()?.url}")
        webView.loadUrl(connInfo()?.url)

        return result
    }

    override suspend fun onBackPressedConsumed() : Boolean {
        val result = Channel<Boolean>()

        val callback =  { it : String? ->
            App.Instance.launchCoroutine { result.send(it in trues) }
            Unit
        };

        getWebView()?.evaluateJavascript(
            "(window.androidBackConsumed === undefined) ? false : window.androidBackConsumed()",
            callback)

        return result.receive()
    }

    override fun isBackButtonEnabled() = _backButtonEnabled

    fun onBackButtonStateChanged(enabled : Boolean) {
        _backButtonEnabled = enabled

        App.Instance.launchCoroutine {
            App.Instance.navigation.send(NavigationRequest._ToolbarBackButtonStateChanged(this))
        }
    }

    override fun getTitle() = _currentTitle

    fun onTitleChanged(currentTitle: String) {
        _currentTitle = currentTitle

        App.Instance.launchCoroutine {
            App.Instance.navigation.send(NavigationRequest._ToolbarTitleChanged(this))
        }
    }
}
