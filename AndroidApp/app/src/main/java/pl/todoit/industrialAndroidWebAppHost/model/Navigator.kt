package pl.todoit.industrialAndroidWebAppHost.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import pl.todoit.industrialAndroidWebAppHost.*
import pl.todoit.industrialAndroidWebAppHost.fragments.*
import pl.todoit.industrialAndroidWebAppHost.model.extensions.sendAndClose
import timber.log.Timber
import java.io.File

class Navigator {
    private var _mainActivity : MainActivity? = null
    private var _webRequestId : String? = null
    private val _navigation = Channel<NavigationRequest>(1) //so it is not blocked when sent from consumer

    suspend fun navigateTo(to:NavigationRequest) = _navigation.send(to)
    fun postNavigateTo(to:NavigationRequest) = App.Instance.launchCoroutine { _navigation.send(to) }

    suspend fun startMainNavigatorLoop() {
        while(true) {
            consumeNavigationRequest(_navigation.receive())
        }
    }

    private suspend fun consumeNavigationRequest(request: NavigationRequest) {
        Timber.d("consumeNavigationRequest() received navigationrequest=$request currentMaster=${_mainActivity?.getCurrentMasterFragment()} currentPopup=${_mainActivity?.getCurrentPopupFragment()} currentActivity=$_mainActivity")

        val act = _mainActivity

        if (act == null) {
            when (request) {
                is NavigationRequest._Activity_MainActivityActivated -> {
                    Timber.d("currentMainActivity=$_mainActivity replacing with newMainActivity=${request.act}")

                    _mainActivity = request.act

                    request.act.removePopupFragmentIfNeeded()

                    navigateTo(NavigationRequest._Activity_GoToBrowser(request.maybeRequestedUrl))
                }
                else -> {
                    Timber.e("_mainActivity may not be null when handling NavigationRequest=$request")
                }
            }
            return
        }

        when (request) {
            is NavigationRequest._Activity_MainActivityInactivated -> {
                Timber.d("currentMainActivity=$_mainActivity inactivating activity=${request.act} same ones?=${_mainActivity == request.act}")
                _mainActivity = null
            }

            is NavigationRequest._Activity_GoToBrowser -> {
                val connInfo =
                    if (request.maybeUrl != null) {
                        App.Instance.getConnectionByUrl(request.maybeUrl)
                            ?: App.Instance.getConnectionManagerNewUrl(request.maybeUrl)
                    } else {
                        App.Instance.getConnectionMenuUrl(ConnectionManagerMode.ConnectionChooser())
                    }

                replaceMasterWithWebBrowser(act, connInfo)
            }
            is NavigationRequest._Toolbar_GoToConnectionSettings ->
                replaceMasterWithWebBrowser(act, App.Instance.getConnectionManagerEditUrl(App.Instance.currentConnection))
            is NavigationRequest._Toolbar_ItemActivated -> {
                val maybeWebApp = act.getCurrentMasterFragment()

                if (maybeWebApp !is WebViewFragment) {
                    Timber.d("ignored change because webview is not within current base fragment")
                    return
                }

                maybeWebApp.onNotifyWebAppAboutMenuItemActivated(request.mi)
            }
            is NavigationRequest.WebBrowser_RegisterMediaAssetIfNeeded -> {
                var success = false
                var hashedContentAsFileName = ""

                try {
                    val bytes = request.fileContent.split(',').map { it.toInt().toUByte().toByte() }.toByteArray()
                    hashedContentAsFileName = bytes.unsecureHashAsSafeFileName()

                    Timber.d("registerMediaAsset webRequestId=${request.webRequestId} will maybe-be-stored as $hashedContentAsFileName")

                    App.Instance.launch(Dispatchers.IO) {
                        val outp = File(act.cacheDir, hashedContentAsFileName)

                        val alreadyExists = outp.exists()
                        Timber.d("registering media asset fileName=$hashedContentAsFileName already exists?=$alreadyExists")

                        if (!alreadyExists) {
                            outp.writeBytes(bytes)
                        }
                    }
                    success = true
                } finally {
                    Timber.d("registerMediaAsset webRequestId=${request.webRequestId} success?=$success")
                    request.continuation(if (success) hashedContentAsFileName else "")
                }
            }
            is NavigationRequest.WebBrowser_SetScanOverlayImage -> {
                Timber.d("setting scan overlay image to ${request.mediaAssetIdentifier}")
                App.Instance.overlayImageOnPause = File(act.cacheDir, request.mediaAssetIdentifier)
            }
            is NavigationRequest.WebBrowser_SetScanSuccessSound -> {
                Timber.d("setting scan success sound to ${request.mediaAssetIdentifier}")
                val f = File(act.cacheDir, request.mediaAssetIdentifier)
                App.Instance.soundSuccessScan = f
                act.updateSoundSuccessScan(f)
            }
            is NavigationRequest.WebBrowser_RequestedTakePhoto -> {
                //TODO see if currently doesn't have another active popup such as barcode scanner
                replacePopupWithTakePhoto(act, request.req)
            }
            is NavigationRequest.TakePhoto_Back -> {
                if (_mainActivity?.getCurrentPopupFragment() !is TakePhotoFragment) {
                    Timber.e("current popup doesn't seem to be TakePhotoFragment")
                    return
                }
                act.removePopupFragmentIfNeeded()
            }
            is NavigationRequest.WebBrowser_RequestedScanQr -> {
                if (_webRequestId != null) {
                    Timber.d("rejected request to start scanner as former scan request is still active")
                    request.req.scanResult.send(ScannerStateChange.Cancelled())
                    request.req.scanResult.sendAndClose(ScannerStateChange.Disposed())
                    return
                }

                if (replacePopupWithScanQr(act, request.req, App.Instance.overlayImageOnPause)) {
                    _webRequestId = request.req.webRequestId
                }
            }
            is NavigationRequest.WebBrowser_ResumeScanQr -> {
                val currentPopup = act.getCurrentPopupFragment()
                if (currentPopup !is ScanQrFragment || _webRequestId != request.webRequestId) {
                    Timber.e("resuming request ignored because scanner is not active anymore OR webRequestId not matches")
                    return
                }

                Timber.d("requesting resuming scanning because scanner is still active AND webRequestId matches")
                currentPopup.onReceivedScanningResumeRequest()
            }
            is NavigationRequest.WebBrowser_CancelScanQr -> {
                val currentPopup = act.getCurrentPopupFragment()
                if (currentPopup !is ScanQrFragment || _webRequestId != request.webRequestId) {
                    Timber.e("cancellation request ignored because scanner is not active anymore OR webRequestId not matches")
                    return
                }

                Timber.d("requesting cancellation of scanning because scanner is still active AND webRequestId matches")
                currentPopup.onReceivedScanningCancellationRequest()
            }
            is NavigationRequest.ScanQr_Scanned -> {
                //TODO see if current popup is actually ScanQrFragment
                act.removePopupFragmentIfNeeded()
                _webRequestId = null
            }
            is NavigationRequest.ScanQr_Back -> {
                //TODO see if current popup is actually ScanQrFragment
                act.removePopupFragmentIfNeeded()
                _webRequestId = null
            }
            is NavigationRequest._ToolbarBackButtonStateChanged -> {
                if (request.sender != act.getCurrentMasterFragment()) {
                    Timber.d("ignored change back button state request from inactive master fragment")
                    return
                }

                act.setToolbarBackButtonState(request.sender.isBackButtonEnabled())
            }
            is NavigationRequest._ToolbarTitleChanged -> {
                if (request.sender != act.getCurrentMasterFragment()) {
                    Timber.d("ignored change name request from inactive master fragment")
                    return
                }

                act.setToolbarTitle(request.sender.getTitle())
            }
            is NavigationRequest.WebBrowser_ToolbarSearchChanged -> {
                if (request.sender != act.getCurrentMasterFragment()) {
                    Timber.d("ignored search action toggler request from inactive master fragment")
                    return
                }

                act.setToolbarSearchState(request.isActive)
            }
            is NavigationRequest.WebBrowser_ToolbarColorsChanged -> {
                if (request.sender != act.getCurrentMasterFragment()) {
                    Timber.d("ignored color change request from inactive master fragment")
                    return
                }

                act.setToolbarColors(request.backgroundColor, request.foregroundColor)
            }
            is NavigationRequest.WebBrowser_ToolbarMenuChanged -> {
                if (request.sender != act.getCurrentMasterFragment()) {
                    Timber.d("ignored change toolbar request from inactive master fragment")
                    return
                }

                val menuCreationErr = act.setAppBarMenuItems(request.menuItems)
                if (menuCreationErr != null) {
                    Timber.e(menuCreationErr)
                }
            }
            is NavigationRequest._Activity_Back -> {
                val currentPopup = act.getCurrentPopupFragment()
                val currentMaster = act.getCurrentMasterFragment()

                if (currentPopup is IProcessesBackButtonEvents) {
                    Timber.d("delegating back button to popup fragment")
                    val backConsumed = currentPopup.onBackPressedConsumed()
                    Timber.d("popup consumed backbutton event?=$backConsumed")
                    if (backConsumed) {
                        return
                    }
                }

                if (currentMaster is IProcessesBackButtonEvents) {
                    //delegate question to master fragment (likely webapp)
                    Timber.d("trying to delegate back button to master fragment")
                    val backConsumed = currentMaster.onBackPressedConsumed()

                    Timber.d("master fragment backButton consumed?=$backConsumed")
                    if (backConsumed) {
                        return
                    }

                    _mainActivity?.finish()
                }

                Timber.d("no fragment is eligible for backbutton processing")
            }
        }
    }

    /**
     * @return true if actually shown. false if request was rejected
     */
    private suspend fun replacePopupWithScanQr(act:MainActivity, scanReq: ScanRequest, overlayImage: File?) =
        act.replacePopupFragment(ScanQrFragment().apply {req = ScanQrReq(scanReq, overlayImage) } )

    private suspend fun replacePopupWithTakePhoto(act : MainActivity, request:SendChannel<File>) =
        act.replacePopupFragment(
            TakePhotoFragment()
                .apply {req = request})

    private fun replaceMasterWithWebBrowser(act:MainActivity, connInfo: ConnectionInfo) {
        App.Instance.currentConnection = connInfo
        act.replaceMasterFragment(WebViewFragment().apply { req = connInfo })
    }
}
