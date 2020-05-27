package pl.todoit.IndustrialWebViewWithQr.model

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import pl.todoit.IndustrialWebViewWithQr.*
import pl.todoit.IndustrialWebViewWithQr.fragments.ScanQrFragment
import pl.todoit.IndustrialWebViewWithQr.fragments.ScannerStateChange
import pl.todoit.IndustrialWebViewWithQr.fragments.TakePhotoFragment
import pl.todoit.IndustrialWebViewWithQr.fragments.WebViewFragment
import pl.todoit.IndustrialWebViewWithQr.model.extensions.sendAndClose
import timber.log.Timber
import java.io.File

class Navigator {
    private var _mainActivity : MainActivity? = null
    private var _scanPromiseId : String? = null
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

            is NavigationRequest.WebBrowser_SetScanSuccessSound -> {
                Timber.d("setting scan success sound")
                App.Instance.setSoundSuccessScan(request.content)
                act.updateSoundSuccessScan()
            }
            is NavigationRequest.WebBrowser_SetScanOverlayImage -> {
                Timber.d("setting scan overlay image")
                App.Instance.overlayImageOnPause = OverlayImage(request.content)
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
                if (_scanPromiseId != null) {
                    Timber.d("rejected request to start scanner as former scan request is still active")
                    request.req.scanResult.sendAndClose(ScannerStateChange.Cancelled())
                    return
                }

                if (replacePopupWithScanQr(act, request.req, App.Instance.overlayImageOnPause)) {
                    _scanPromiseId = request.req.jsPromiseId
                }
            }
            is NavigationRequest.WebBrowser_ResumeScanQr -> {
                val currentPopup = act.getCurrentPopupFragment()
                if (currentPopup !is ScanQrFragment || _scanPromiseId != request.jsPromiseId) {
                    Timber.e("resuming request ignored because scanner is not active anymore OR jsPromiseId not matches")
                    return
                }

                Timber.d("requesting resuming scanning because scanner is still active AND jsPromiseId matches")
                currentPopup.onReceivedScanningResumeRequest()
            }
            is NavigationRequest.WebBrowser_CancelScanQr -> {
                val currentPopup = act.getCurrentPopupFragment()
                if (currentPopup !is ScanQrFragment || _scanPromiseId != request.jsPromiseId) {
                    Timber.e("cancellation request ignored because scanner is not active anymore OR jsPromiseId not matches")
                    return
                }

                Timber.d("requesting cancellation of scanning because scanner is still active AND jsPromiseId matches")
                currentPopup.onReceivedScanningCancellationRequest()
            }
            is NavigationRequest.ScanQr_Scanned -> {
                //TODO see if current popup is actually ScanQrFragment
                act.removePopupFragmentIfNeeded()
                _scanPromiseId = null
            }
            is NavigationRequest.ScanQr_Back -> {
                //TODO see if current popup is actually ScanQrFragment
                act.removePopupFragmentIfNeeded()
                _scanPromiseId = null
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
            is NavigationRequest.WebBrowser_ToolbarMenuChanged -> {
                if (request.sender != act.getCurrentMasterFragment()) {
                    Timber.d("ignored change name request from inactive master fragment")
                    return
                }

                act.setAppBarMenuItems(request.menuItems)
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
    private suspend fun replacePopupWithScanQr(act:MainActivity, scanReq: ScanRequest, overlayImage: OverlayImage?) =
        act.replacePopupFragment(ScanQrFragment().apply {req = Pair(scanReq, overlayImage)} )

    private suspend fun replacePopupWithTakePhoto(act : MainActivity, request:SendChannel<File>) =
        act.replacePopupFragment(
            TakePhotoFragment()
                .apply {req = request})

    private fun replaceMasterWithWebBrowser(act:MainActivity, connInfo: ConnectionInfo) {
        App.Instance.currentConnection = connInfo
        act.replaceMasterFragment(WebViewFragment().apply { req = connInfo })
    }
}
