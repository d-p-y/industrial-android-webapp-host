package pl.todoit.IndustrialWebViewWithQr

import kotlinx.coroutines.channels.SendChannel
import pl.todoit.IndustrialWebViewWithQr.model.ConnectionInfo
import pl.todoit.IndustrialWebViewWithQr.model.IHasTitle
import pl.todoit.IndustrialWebViewWithQr.model.ITogglesBackButtonVisibility
import pl.todoit.IndustrialWebViewWithQr.model.ScanRequest
import java.io.File

/**
 * convention for form initiated navigation (form requests navigation): Sender_ActionName
 * convention for non-form initiated navigation (top level request to navigate to some form): _Sender_ActionName
 */
sealed class NavigationRequest {
    class _Activity_MainActivityActivated(val act:MainActivity, val maybeRequestedUrl : String?) : NavigationRequest()
    class _Activity_MainActivityInactivated(val act:MainActivity) : NavigationRequest()
    class _Activity_GoToBrowser(val maybeUrl : String?) : NavigationRequest()
    class _Toolbar_GoToConnectionSettings() : NavigationRequest()
    class _Activity_Back() : NavigationRequest()
    class _ToolbarTitleChanged(val sender : IHasTitle) : NavigationRequest()
    class _ToolbarBackButtonStateChanged(val sender : ITogglesBackButtonVisibility) : NavigationRequest()
    class WebBrowser_SetScanSuccessSound(val content: ByteArray) : NavigationRequest()
    class WebBrowser_SetScanOverlayImage(val content: ByteArray) : NavigationRequest()
    class WebBrowser_RequestedScanQr(val req: ScanRequest) : NavigationRequest()
    class WebBrowser_ResumeScanQr(val jsPromiseId: String) : NavigationRequest()
    class WebBrowser_CancelScanQr(val jsPromiseId: String) : NavigationRequest()
    class WebBrowser_RequestedTakePhoto(val req:SendChannel<File>) : NavigationRequest()
    class TakePhoto_Back() : NavigationRequest()
    class ScanQr_Scanned() : NavigationRequest()
    class ScanQr_Back() : NavigationRequest()
}
