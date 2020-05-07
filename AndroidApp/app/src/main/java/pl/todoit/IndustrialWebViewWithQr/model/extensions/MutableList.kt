package pl.todoit.IndustrialWebViewWithQr.model.extensions

fun <T> MutableList<T>.popAtMostFirstItems(takeAndRemoveAtMost : Int) : List<T> {
    val result = this.take(takeAndRemoveAtMost)

    (0 until result.size).forEach( {this.removeAt(0) })
    return result
}