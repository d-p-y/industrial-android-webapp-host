package pl.todoit.industrialAndroidWebAppHost

import androidx.fragment.app.Fragment
import kotlinx.coroutines.channels.SendChannel
import pl.todoit.industrialAndroidWebAppHost.model.*
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
    class _Toolbar_ItemActivated(val mi:MenuItemInfo) : NavigationRequest()
    class _Activity_Back() : NavigationRequest()
    class _ToolbarTitleChanged(val sender : IHasTitle) : NavigationRequest()
    class _ToolbarBackButtonStateChanged(val sender : ITogglesBackButtonVisibility) : NavigationRequest()
    class WebBrowser_ToolbarMenuChanged(val sender : Fragment, val menuItems:List<MenuItemInfo>) : NavigationRequest()
    class WebBrowser_ToolbarSearchChanged(val sender : Fragment, val isActive : Boolean) : NavigationRequest()
    class WebBrowser_ToolbarColorsChanged(val sender: Fragment, val backgroundColor : String, val foregroundColor : String) : NavigationRequest()
    class WebBrowser_RegisterMediaAssetIfNeeded(val webRequestId : String, val fileContent : String, val continuation : (String)->Unit ) : NavigationRequest()
    class WebBrowser_SetScanSuccessSound(val mediaAssetIdentifier: String) : NavigationRequest()
    class WebBrowser_SetScanOverlayImage(val mediaAssetIdentifier: String) : NavigationRequest()
    class WebBrowser_RequestedScanQr(val req: ScanRequest) : NavigationRequest()
    class WebBrowser_ResumeScanQr(val webRequestId: String) : NavigationRequest()
    class WebBrowser_CancelScanQr(val webRequestId: String) : NavigationRequest()
    class WebBrowser_RequestedTakePhoto(val req:SendChannel<File>) : NavigationRequest()
    class TakePhoto_Back() : NavigationRequest()
    class ScanQr_Scanned() : NavigationRequest()
    class ScanQr_Back() : NavigationRequest()
}
