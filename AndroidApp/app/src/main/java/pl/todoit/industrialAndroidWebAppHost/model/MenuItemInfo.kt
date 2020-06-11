package pl.todoit.industrialAndroidWebAppHost.model

import kotlinx.serialization.Serializable

@Serializable
data class MenuItemInfo(
    val webMenuItemId:String,
    val trueForAction:Boolean,
    val title:String,
    val iconMediaIdentifierId : String?,
    val enabled:Boolean = true,
    var physicalMenuItemId:Int = 0) {

    fun isAction() = trueForAction
    fun isMenuItem() = !trueForAction
}
