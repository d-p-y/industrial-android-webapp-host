package pl.todoit.IndustrialWebViewWithQr.fragments.barcodeDecoding

class WorkerStats(val allItemsCount:Int, val ofWhichProcessed:Int, var ofWhichSkipped:Int, val workingTimeMs:Long) {
    fun addSkipped(skipped: Int) {
        ofWhichSkipped+=skipped
    }

    fun itemsConsumedPercent()  = if (allItemsCount != 0) (ofWhichProcessed.toFloat() / allItemsCount) else -1.0f
    fun timeSpentPerItemMs() = if (ofWhichProcessed != 0) (workingTimeMs.toFloat() / ofWhichProcessed) else -1.0f
}