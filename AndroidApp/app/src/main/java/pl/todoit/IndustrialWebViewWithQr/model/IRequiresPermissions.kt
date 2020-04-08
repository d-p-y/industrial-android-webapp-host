package pl.todoit.IndustrialWebViewWithQr.model

interface IRequiresPermissions {
    fun getRequiredAndroidManifestPermissions() : Array<String>
    fun onRequiredPermissionRejected(perm:String)
}

