package pl.todoit.IndustrialWebViewWithQr.model

import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.Serializable
import timber.log.Timber

@Serializable
open class LayoutStrategy(var typeName:String) {}

@Serializable
class FitScreenLayoutStrategy(
    val screenTitle:String?=null) : LayoutStrategy(FitScreenLayoutStrategy::class.simpleName!!) {}

@Serializable
class MatchWidthWithFixedHeightLayoutStrategy (
    val paddingTopMm:Int,
    val heightMm:Int) : LayoutStrategy(MatchWidthWithFixedHeightLayoutStrategy::class.simpleName!!) {}

class ScanRequest(
    val scanResult : SendChannel<String?>,
    val layoutStrategy : LayoutStrategy) {}

fun deserializeLayoutStrategy(layoutStrategyAsJson:String) : LayoutStrategy {
    val baseType = jsonForgiving.parse(LayoutStrategy.serializer(), layoutStrategyAsJson)

    return when(baseType.typeName) {
        FitScreenLayoutStrategy::class.simpleName -> jsonStrict.parse(FitScreenLayoutStrategy.serializer(), layoutStrategyAsJson)
        MatchWidthWithFixedHeightLayoutStrategy::class.simpleName -> jsonStrict.parse(MatchWidthWithFixedHeightLayoutStrategy.serializer(), layoutStrategyAsJson)
        else -> {
            Timber.e("unknown strategy ${baseType.typeName}")
            FitScreenLayoutStrategy()}
    }
}
