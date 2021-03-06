package pl.todoit.industrialAndroidWebAppHost.model

import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import pl.todoit.industrialAndroidWebAppHost.fragments.ScannerStateChange
import timber.log.Timber

@Serializable
open class LayoutStrategy(var typeName:String) {}

@Serializable
class FillScreenLayoutStrategy(
    val hideToolbar:Boolean = false,
    val screenTitle:String?=null) : LayoutStrategy(FillScreenLayoutStrategy::class.simpleName!!) {}

@Serializable
class MatchWidthWithFixedHeightLayoutStrategy (
    val paddingOriginIsTop:Boolean = true,
    val paddingMm:Int,
    val heightMm:Int) : LayoutStrategy(MatchWidthWithFixedHeightLayoutStrategy::class.simpleName!!) {}

class ScanRequest(
    val webRequestId : String,
    val postSuccess : PauseOrFinish,
    val layoutStrategy : LayoutStrategy) {
    val scanResult = BroadcastChannel<ScannerStateChange>(1)
}

fun deserializeLayoutStrategy(layoutStrategyAsJson:String) : LayoutStrategy {
    val baseType = jsonForgiving.decodeFromString<LayoutStrategy>(layoutStrategyAsJson)

    return when(baseType.typeName) {
        FillScreenLayoutStrategy::class.simpleName ->
            jsonStrict.decodeFromString<FillScreenLayoutStrategy>(layoutStrategyAsJson)
        MatchWidthWithFixedHeightLayoutStrategy::class.simpleName ->
            jsonStrict.decodeFromString<MatchWidthWithFixedHeightLayoutStrategy>(layoutStrategyAsJson)
        else -> {
            Timber.e("unknown strategy ${baseType.typeName}")
            FillScreenLayoutStrategy()}
    }
}
