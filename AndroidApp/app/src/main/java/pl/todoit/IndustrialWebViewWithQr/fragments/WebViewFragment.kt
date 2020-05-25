package pl.todoit.IndustrialWebViewWithQr.fragments

import android.annotation.SuppressLint
import android.content.Context
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
import kotlinx.coroutines.channels.receiveOrNull
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.list
import pl.todoit.IndustrialWebViewWithQr.App
import pl.todoit.IndustrialWebViewWithQr.MainActivity
import pl.todoit.IndustrialWebViewWithQr.NavigationRequest
import pl.todoit.IndustrialWebViewWithQr.R
import pl.todoit.IndustrialWebViewWithQr.fragments.barcodeDecoding.ProcessorSuccess
import pl.todoit.IndustrialWebViewWithQr.model.*
import timber.log.Timber
import java.io.File

fun String.escapeJsonForWebView() = replace("\"", "\\\"")

fun postReply(host : WebViewFragment, reply : AndroidReply) {
    var replyJson = jsonStrict.stringify(AndroidReply.serializer(), reply)
    var msg =
        "androidPostReplyToPromise(\"" +
        replyJson.escapeJsonForWebView() +
        "\")"
    host.getWebView()?.evaluateJavascript(msg, null)
}

sealed class ScannerStateChange {
    class Scanned(val barcode : ProcessorSuccess<String>) : ScannerStateChange()
    class Paused() : ScannerStateChange()
    class Resumed() : ScannerStateChange()
    class Cancelled() : ScannerStateChange()
}

sealed class SensitiveWebExposedOperation(val caller : ConnectionInfo? = App.Instance.getConnectionByUrl(App.Instance.currentConnection.url)) {
    class GetKnownConnections() : SensitiveWebExposedOperation()
    class SaveConnection(val conn:ConnectionInfo) : SensitiveWebExposedOperation()
    class CreateShortcut(val conn:ConnectionInfo?) : SensitiveWebExposedOperation()
    class FinishConnectionManager(val maybeUrl : String?) : SensitiveWebExposedOperation()
}

fun maybeExecuteSensitiveOperation(act:Context?, inp:SensitiveWebExposedOperation) : String =
    when(inp) {
        is SensitiveWebExposedOperation.GetKnownConnections ->
            jsonStrict.stringify(
                ConnectionInfo.serializer().list,
                if (inp.caller?.mayManageConnections == true) App.Instance.knownConnections.filter { !it.isConnectionManager } else listOf())
        is SensitiveWebExposedOperation.CreateShortcut ->
            if (inp.caller?.mayManageConnections == true && inp.conn != null && act != null) {
                if (App.Instance.createShortcut(act, inp.conn)) "true" else "false"
            } else "false"
        is SensitiveWebExposedOperation.SaveConnection ->
            if (inp.caller?.mayManageConnections == true) {
                App.Instance.persistConnection(inp.conn)
                "true"
            } else "false"
        is SensitiveWebExposedOperation.FinishConnectionManager ->
            if (inp.caller?.mayManageConnections == true) {
                App.Instance.navigator.postNavigateTo(NavigationRequest._Activity_GoToBrowser(inp.maybeUrl))
                ""
            } else ""
    }

class WebViewExposedMethods(private var host: WebViewFragment) {
    @JavascriptInterface
    fun finishConnectionManager(maybeUrl : String) =
        maybeExecuteSensitiveOperation(
            host.activity,
            SensitiveWebExposedOperation.FinishConnectionManager(maybeUrl) )

    @JavascriptInterface
    fun createShortcut(url : String) =
        maybeExecuteSensitiveOperation(
            host.activity,
            SensitiveWebExposedOperation.CreateShortcut(
                App.Instance.getConnectionByUrl(url)))

    @JavascriptInterface
    fun getKnownConnections() =
        maybeExecuteSensitiveOperation(
            host.activity,
            SensitiveWebExposedOperation.GetKnownConnections())

    @JavascriptInterface
    fun saveConnection(connInfoAsJson : String) =
        maybeExecuteSensitiveOperation(
            host.activity,
            SensitiveWebExposedOperation.SaveConnection(
                jsonStrict.parse(ConnectionInfo.serializer(), connInfoAsJson)))

    @JavascriptInterface
    fun setScanSuccessSound(fileContent : String) =
        App.Instance.navigator.postNavigateTo(
            NavigationRequest.WebBrowser_SetScanSuccessSound(
                fileContent.split(',').map { it.toInt().toUByte().toByte() }.toByteArray()))

    @JavascriptInterface
    fun setPausedScanOverlayImage(fileContent : String) =
        App.Instance.navigator.postNavigateTo(
            NavigationRequest.WebBrowser_SetScanOverlayImage(
                fileContent.split(',').map { it.toInt().toUByte().toByte() }.toByteArray()))

    @JavascriptInterface
    fun openInBrowser(url: String) {
        val act = host.activity

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

class WebViewFragment : Fragment(), IHasTitle, ITogglesBackButtonVisibility, IProcessesBackButtonEvents, ITogglesToolbarVisibility {
    lateinit var req : ConnectionInfo

    private var _currentTitle : String = "Untitled WebApp"
    private var _backButtonEnabled = false

    //need to maintain URL manually as attempting to call WebView.getUrl() from @JavascriptInterface annotated methods results with
    //W/WebView: java.lang.Throwable: A WebView method was called on thread 'JavaBridge'. All WebView methods must be called on the same thread.

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

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?) : Boolean {
                if (url == null) {
                    return false
                }

                App.Instance.currentConnection = App.Instance.getOrCreateConnectionByUrl(url)

                if (!url.contains("#") && App.Instance.currentConnection.webAppPersistentState != null) {
                    //no state yet but can retrieve it

                    App.Instance.launchCoroutine {
                        webView.loadUrl(App.Instance.currentConnection.buildUrlWithState())
                    }
                }

                return false //don't start regular browser
            }
        }
        webView.addJavascriptInterface(WebViewExposedMethods(this), "Android")


        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                if (req.forwardConsoleLogToLogCat) {
                    Timber.d("received console.log: ${msg?.sourceId()}:${msg?.lineNumber()} ${msg?.message()}")
                }
                return true
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                onTitleChanged(title ?: "")
            }

            override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?) : Boolean {
                if (!App.Instance.currentConnection.hasPermissionToTakePhoto) {
                    Timber.d("connection doesn't have permission to take photo")
                    return false
                }

                if (filePathCallback == null) {
                    Timber.d("onShowFileChooser() got null filePathCallback")
                    return false
                }

                App.Instance.launchCoroutine {
                    val req = Channel<File?>()
                    App.Instance.navigator.navigateTo(NavigationRequest.WebBrowser_RequestedTakePhoto(req))

                    when(val x = req.receiveOrNull()) {
                        is File -> {
                            val rawPath = x.absolutePath
                            val uriPath = Uri.parse("file://"+rawPath)
                            Timber.d("onShowFileChooser() got rawPath=$rawPath that was converted to uriPath=$uriPath")
                            filePathCallback.onReceiveValue(arrayOf(uriPath))
                        }
                        else -> {
                            Timber.d("onShowFileChooser() got cancellation")
                            filePathCallback.onReceiveValue(arrayOf())
                        }
                    }
                }
                return true
            }
        }

        WebView.setWebContentsDebuggingEnabled(req.remoteDebuggerEnabled)

        if (req.forceReloadFromNet) {
            webView.clearCache(true)
        }

        val urlWithState = req.buildUrlWithState()
        Timber.d("navigating to $urlWithState")

        //next line is needed because loadUrl doesn't invoke shouldOverrideUrlLoading()
        App.Instance.currentConnection = App.Instance.getOrCreateConnectionByUrl(req.url)

        webView.loadUrl(urlWithState)

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

    //TODO implement web API to toggle it (needing ConnectionInfo permission being true by default)
    override fun isToolbarVisible(): Boolean = true

    fun maybeGetWebAppUrl() : String? = getWebView()?.url
}
