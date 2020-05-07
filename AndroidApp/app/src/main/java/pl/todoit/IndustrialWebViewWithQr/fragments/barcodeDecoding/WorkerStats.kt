package pl.todoit.IndustrialWebViewWithQr.fragments.barcodeDecoding

class WorkerStats(
    val productingEveryMs:Int,
    val consumingEveryMs:Int,
    val allItemsCount:Int,
    val ofWhichProcessed:Int,
    var ofWhichSkipped:Int,
    var ofWhichIgnored:Int,
    val batchWorkingTimeMs:Int,
    val workerTimeMs : Int
) {
    var skippedByProducer = 0

    fun addSkipped(skipped: Int) {
        skippedByProducer+=skipped
    }

    fun itemsConsumedPercent() = if (allItemsCount != 0) ((ofWhichProcessed*100) / (skippedByProducer + allItemsCount - ofWhichIgnored)) else -1

    /** to show gains thanks to parallelism */
    fun processingTimePerItemMs() = if (ofWhichProcessed != 0) (batchWorkingTimeMs.toFloat() / ofWhichProcessed) else -1.0f

    /** to benchmark barcode decoding library  */
    fun oneItemDecodeTimeMs() = if (ofWhichProcessed != 0) (workerTimeMs.toFloat() / ofWhichProcessed) else -1.0f
}
