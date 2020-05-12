package pl.todoit.IndustrialWebViewWithQr.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
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
import pl.todoit.IndustrialWebViewWithQr.fragments.barcodeDecoding.ProcessorSuccess
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

sealed class ScannerStateChange {
    class Scanned(val barcode : ProcessorSuccess<String>) : ScannerStateChange()
    class Paused() : ScannerStateChange()
    class Resumed() : ScannerStateChange()
    class Cancelled() : ScannerStateChange()
}

class WebViewExposedMethods(private var host: WebViewFragment) {

    @JavascriptInterface
    fun setPausedScanOverlayImage(fileContent : String) =
        App.Instance.navigator.postNavigateTo(
            NavigationRequest.WebBrowser_SetScanOverlayImage(
                fileContent.split(',').map { it.toInt().toUByte().toByte() }.toByteArray()))

    @JavascriptInterface
    fun openInBrowser(url: String) {
        val act = host?.activity

        if (act == null) {
            Timber.e("null activity")
            return
        }

        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val browserAct = browserIntent.resolveActivity(act.packageManager)
        if (browserAct == null) {
            Timber.e("no default browser")
            return
        }

        act.startActivity(browserIntent)
    }

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
            val req = ScanRequest(
                promiseId,
                if (!asksJsForValidation) PauseOrFinish.Finish else PauseOrFinish.Pause,
                deserializeLayoutStrategy(layoutStrategyAsJson))

            val decoderReplyChannel = req.scanResult.openSubscription()

            App.Instance.navigator.navigateTo(NavigationRequest.WebBrowser_RequestedScanQr(req))

            for (rawReply in decoderReplyChannel) {
                val maybeReply =
                    when(rawReply) {
                        is ScannerStateChange.Scanned -> {
                            Timber.d("decoderReplyChannel got: scanned")
                            //showToast("${rawReply.barcode.stats.itemsConsumedPercent()}% ${rawReply.barcode.stats.productingEveryMs}->${rawReply.barcode.stats.consumingEveryMs}", true)
                            AndroidReply(
                                PromiseId = promiseId,
                                IsCancellation = false,
                                Barcode = rawReply.barcode.result)
                        }
                        is ScannerStateChange.Cancelled -> {
                            Timber.d("decoderReplyChannel got: cancelled")
                            AndroidReply(
                                PromiseId = promiseId,
                                IsCancellation = true,
                                Barcode = null)
                        }
                        is ScannerStateChange.Paused -> {
                            Timber.d("decoderReplyChannel got: paused")
                            null
                        }
                        is ScannerStateChange.Resumed -> {
                            Timber.d("decoderReplyChannel got: resumed")
                            null
                        }
                    }

                if (maybeReply != null) {
                    postReply(host, maybeReply)
                }
            }
            Timber.d("decoderReplyChannel finished with requestScanQr($promiseId) due to channel closure")
        }
    }

    @JavascriptInterface
    fun resumeScanQr(promiseId : String) =
        App.Instance.navigator.postNavigateTo(NavigationRequest.WebBrowser_ResumeScanQr(promiseId))

    @JavascriptInterface
    fun cancelScanQr(promiseId : String) =
        App.Instance.navigator.postNavigateTo(NavigationRequest.WebBrowser_CancelScanQr(promiseId))
}

val trues = arrayOf("true")

class WebViewFragment : Fragment(), IHasTitle, ITogglesBackButtonVisibility, IProcessesBackButtonEvents {
    lateinit var req : ConnectionInfo

    private var _currentTitle : String = "Untitled WebApp"
    private var _backButtonEnabled = false

    fun getWebView() : WebView? = view?.findViewById(R.id.webView)


    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var result = inflater.inflate(R.layout.fragment_web_view, container, false)
        var webView = result.findViewById<WebView>(R.id.webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.loadsImagesAutomatically = true
        webView.settings.allowUniversalAccessFromFileURLs = true //otherwise XMLHttpRequest to assets don't work due to CORS problem
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                Timber.d("onReceivedError errorCode=$errorCode description=$description failingUrl=$failingUrl")
                super.onReceivedError(view, errorCode, description, failingUrl)
            }

            //don't start regular browser
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?) = false
        }
        webView.addJavascriptInterface(WebViewExposedMethods(this), "Android")

        if (req.forwardConsoleLogToLogCat == true) {
            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                    Timber.d("received console.log: ${msg?.sourceId()}:${msg?.lineNumber()} ${msg?.message()}")
                    return true
                }
            }
        }

        WebView.setWebContentsDebuggingEnabled(req.remoteDebuggerEnabled == true)

        if (req.forceReloadFromNet == true) {
            webView.clearCache(true)
        }

        Timber.d("navigating to ${req.url}")
        webView.loadUrl(req.url)

        return result
    }

    override suspend fun onBackPressedConsumed() : Boolean {
        val result = Channel<Boolean>()

        getWebView()?.evaluateJavascript(
            "(window.androidBackConsumed === undefined) ? false : window.androidBackConsumed()",
            { App.Instance.launchCoroutine { result.send(it in trues) } })

        return result.receive()
    }

    override fun isBackButtonEnabled() = _backButtonEnabled

    fun onBackButtonStateChanged(enabled : Boolean) {
        _backButtonEnabled = enabled
        App.Instance.navigator.postNavigateTo(NavigationRequest._ToolbarBackButtonStateChanged(this))
    }

    override fun getTitle() = _currentTitle

    fun onTitleChanged(currentTitle: String) {
        _currentTitle = currentTitle
        App.Instance.navigator.postNavigateTo(NavigationRequest._ToolbarTitleChanged(this))
    }
}
