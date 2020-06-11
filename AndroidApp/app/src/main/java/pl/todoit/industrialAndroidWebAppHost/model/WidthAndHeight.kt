package pl.todoit.industrialAndroidWebAppHost.model

class WidthAndHeight(
    val width : Int,
    val height : Int) {

    fun biggerDim() = Math.max(width, height)
}
