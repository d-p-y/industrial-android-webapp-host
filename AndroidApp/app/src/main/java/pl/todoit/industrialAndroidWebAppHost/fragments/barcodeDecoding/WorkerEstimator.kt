package pl.todoit.industrialAndroidWebAppHost.fragments.barcodeDecoding

import pl.todoit.industrialAndroidWebAppHost.App
import pl.todoit.industrialAndroidWebAppHost.model.extensions.diffInMilisecTo
import timber.log.Timber
import java.util.*

class WorkerEstimator(val maxSimultaneousComputations : Int) {
    private val _producerQueueMaxSize = App.Instance.imagesToDecodeQueueSize
    private val _consumeAtLeastEveryMs = App.Instance.decodeAtLeastOnceEveryMs

    private var _batchConsumeStarted : Date? = null
    private var _batchesCount = 0

    private var _batchConsumeTimeMs = 0
    private var _workerConsumingTimeMs = 0

    private var _producingTimeMs = 0

    private var _receivedBatchSize : Int? = null
    private var _receivedAt : Date? = null
    private var _lastConsumedAt : Date? = null
    private var _totalReceivedCount = 0
    private var _totalConsumedCount = 0
    private var _totalSkippedCount = 0
    private var _totalUnneededCount = 0

    fun createSummary() =
        WorkerStats(
            getProducingAvgTimeMs() ?: -1,
            getBatchConsumingAvgTimeMs() ?: -1,
            _totalReceivedCount,
            _totalConsumedCount,
            _totalSkippedCount,
            _totalUnneededCount,
            _batchConsumeTimeMs,
            _workerConsumingTimeMs
        )

    /** how much time it takes to process several frames at once using maximum parallelism */
    private fun getBatchConsumingAvgTimeMs() = if (_batchesCount > 0) (_batchConsumeTimeMs / _batchesCount) else null

    private fun getProducingAvgTimeMs() = if (_totalReceivedCount > 0) (_producingTimeMs / _totalReceivedCount) else null

    fun consumerMeasurementRegister(millis : Int) {
        _totalConsumedCount++
        _lastConsumedAt = Date()
        _workerConsumingTimeMs += millis
    }

    fun measureProducer(millisToProduce:List<Int>) {
        _totalReceivedCount += millisToProduce.size
        _producingTimeMs += millisToProduce.sum()

        _receivedAt = Date()
        _receivedBatchSize = millisToProduce.size
    }

    fun ignoreItemsFollowingSuccess(toIgnoreCount: Int) {
        _totalUnneededCount += toIgnoreCount
    }

    fun shouldConsumeItemsCount() : Int? {
        val receivedAt = _receivedAt
        val receivedBatchSize = _receivedBatchSize
        val lastConsumedAt = _lastConsumedAt

        val avgBatchConsumingTimeMs = getBatchConsumingAvgTimeMs()
        val avgProducingTimeMsAvg = getProducingAvgTimeMs()

        if (avgBatchConsumingTimeMs == null || avgProducingTimeMsAvg == null || receivedAt == null || lastConsumedAt == null || receivedBatchSize == null) {
            return null //not possible to estimate anything yet. Wait till have more data for estimation
        }

        val elapsedMilisecSinceBatchStart = receivedAt.diffInMilisecTo(Date())
        val likelyHavePendingItems = Math.ceil(elapsedMilisecSinceBatchStart.toDouble() / avgProducingTimeMsAvg).toInt()
        val timeLeftUntilQueueOverflowMs = (_producerQueueMaxSize - likelyHavePendingItems) * avgProducingTimeMsAvg

        //'floor' would be safest here (producer would get open channel always) with smallest chance of loosing most recent frames
        //'round' depending on value would either cause some starvation or queue overflow
        //'ceil' to favor full consumer utilization over queue overflow on producer side
        val timeAllowsToConsumeItemsCount =
            Math.floor(timeLeftUntilQueueOverflowMs.toDouble() / avgBatchConsumingTimeMs).toInt() * maxSimultaneousComputations

        val lastConsumeWasAgo = lastConsumedAt.diffInMilisecTo(Date())
        val leftToConsume = _totalReceivedCount - _totalConsumedCount - _totalSkippedCount
        val wouldConsumeItemsCount = Math.min(leftToConsume, timeAllowsToConsumeItemsCount)

        //starving protection
        val forceAtLeastOneToConsume = lastConsumeWasAgo > _consumeAtLeastEveryMs
        val willConsumeItemsCount = if (forceAtLeastOneToConsume) Math.min(1, wouldConsumeItemsCount) else wouldConsumeItemsCount
        val increaseSkipBy = leftToConsume - willConsumeItemsCount

        Timber.d(
            """barcodeDecoderWorker time=${Date().time} mayProcessItemsSimultaneously=$maxSimultaneousComputations
receivedBatchSize=$receivedBatchSize batchesCount=$_batchesCount received=$_totalReceivedCount consumed=$_totalConsumedCount skipped=$_totalSkippedCount left=$leftToConsume
avgBatchConsumingTime=$avgBatchConsumingTimeMs[ms] avgProducing=$avgProducingTimeMsAvg[ms] decodingLibrarySpeed=${_workerConsumingTimeMs/_totalConsumedCount}[ms]
timeSinceBatchStart=$elapsedMilisecSinceBatchStart[ms] 
likelyHavePendingItems=$likelyHavePendingItems 
lastConsumeWasAgo=$lastConsumeWasAgo[ms] 
timeLeftUntilQueueOverflow=$timeLeftUntilQueueOverflowMs[ms] 
timeAllowsToConsumeItemsCount=$timeAllowsToConsumeItemsCount 
forceAtLeastOneToConsume=$forceAtLeastOneToConsume
wouldConsumeItemsCount=$wouldConsumeItemsCount
willConsumeItemsCount=$willConsumeItemsCount
increaseSkipBy=$increaseSkipBy"""
        )

        _totalSkippedCount += increaseSkipBy

        return willConsumeItemsCount
    }

    fun batchConsumeStarts() {
        _batchConsumeStarted = Date()
    }

    fun batchConsumeEnds() {
        val started = _batchConsumeStarted

        if (started == null) {
            Timber.e("bug: batchConsumeStarts() not called")
            return
        }

        _batchesCount++
        _batchConsumeStarted = null
        _batchConsumeTimeMs += started.diffInMilisecTo(Date()).toInt()
    }
}
