package pl.todoit.IndustrialWebViewWithQr.model

import kotlinx.coroutines.channels.Channel
import pl.todoit.IndustrialWebViewWithQr.App
import pl.todoit.IndustrialWebViewWithQr.MainActivity
import pl.todoit.IndustrialWebViewWithQr.NavigationRequest
import pl.todoit.IndustrialWebViewWithQr.OverlayImage
import pl.todoit.IndustrialWebViewWithQr.fragments.ConnectionsSettingsFragment
import pl.todoit.IndustrialWebViewWithQr.fragments.ScanQrFragment
import pl.todoit.IndustrialWebViewWithQr.fragments.ScannerStateChange
import pl.todoit.IndustrialWebViewWithQr.fragments.WebViewFragment
import pl.todoit.IndustrialWebViewWithQr.model.extensions.sendAndClose
import timber.log.Timber

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

                    if (request.maybeRequestedUrl != null) {
                        App.Instance.initializeConnection(request.maybeRequestedUrl)
                    }

                    //TODO if no url given (=program not started from shortcut) show known connections menu/list instead
                    navigateTo(NavigationRequest._Activity_GoToBrowser())
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

            is NavigationRequest._Activity_GoToBrowser -> replaceMasterWithWebBrowser(act, App.Instance.currentConnection)
            is NavigationRequest._Toolbar_GoToConnectionSettings -> replaceMasterWithConnectionsSettings(act, App.Instance.currentConnection)
            is NavigationRequest.ConnectionSettings_Save -> {
                App.Instance.currentConnection = request.connInfo
                replaceMasterWithWebBrowser(act, App.Instance.currentConnection)
            }
            is NavigationRequest.ConnectionSettings_Back -> replaceMasterWithWebBrowser(act, App.Instance.currentConnection)
            is NavigationRequest.WebBrowser_SetScanOverlayImage -> {
                Timber.d("setting scan overlay image")
                App.Instance.overlayImageOnPause = OverlayImage(request.content)
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
                act.removePopupFragmentIfNeeded()
                _scanPromiseId = null
            }
            is NavigationRequest.ScanQr_Back -> {
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
    private suspend fun replacePopupWithScanQr(act:MainActivity, scanReq: ScanRequest, overlayImage: OverlayImage?) : Boolean {
        App.Instance.scanQrFragmentParams.set(Pair(scanReq, overlayImage))
        val fragment = ScanQrFragment()
        return act.replacePopupFragment(fragment)
    }

    private fun replaceMasterWithWebBrowser(act:MainActivity, connInfo: ConnectionInfo) {
        App.Instance.webViewFragmentParams.set(connInfo)
        val fragment = WebViewFragment()
        act.replaceMasterFragment(fragment)
    }

    private fun replaceMasterWithConnectionsSettings(act:MainActivity, connInfo: ConnectionInfo) {
        App.Instance.connSettFragmentParams.set(connInfo)
        val fragment = ConnectionsSettingsFragment()
        act.replaceMasterFragment(fragment)
    }
}
