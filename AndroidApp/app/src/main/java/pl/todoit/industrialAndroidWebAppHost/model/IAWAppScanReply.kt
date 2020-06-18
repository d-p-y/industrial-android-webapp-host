package pl.todoit.industrialAndroidWebAppHost.model

import kotlinx.serialization.Serializable

@Serializable
data class IAWAppScanReply (
    var WebRequestId : String,
    var IsCancellation : Boolean,
    var Barcode : String?
)
