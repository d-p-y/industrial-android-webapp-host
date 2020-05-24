package pl.todoit.IndustrialWebViewWithQr.model

enum class PermissionRequestRejected {
    MayOpenFragment,
    MayNotOpenFragment
}

interface IRequiresPermissions {
    fun getNeededAndroidManifestPermissions() : Array<String>
    fun onNeededPermissionRejected(rejectedPerms:List<String>) : PermissionRequestRejected
}
