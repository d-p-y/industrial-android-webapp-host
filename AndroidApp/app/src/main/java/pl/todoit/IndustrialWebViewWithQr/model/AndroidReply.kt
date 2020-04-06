package pl.todoit.IndustrialWebViewWithQr.model

import kotlinx.serialization.Serializable

@Serializable
data class AndroidReply (
    var PromiseId : String,
    var IsSuccess : Boolean,
    var Reply : String?
)