package pl.todoit.IndustrialWebViewWithQr.fragments.barcodeDecoding

import pl.todoit.IndustrialWebViewWithQr.App
import pl.todoit.IndustrialWebViewWithQr.model.extensions.diffInMilisecTo
import timber.log.Timber
import java.util.*

class WorkerEstimator {
    private val _producerQueueMaxSize = App.Instance.imagesToDecodeQueueSize
    private val _consumeAtLeastEveryMs = App.Instance.decodeAtLeastOnceEveryMs
    private val _sufficientStatsSize = App.Instance.sufficientStatsSize
    private val _consumingTimesMs = mutableListOf<Long>()
    private var _consumingTimeMsAvg : Long? = null

    private val _producingTimesMs = mutableListOf<Long>()
    private var _producingTimeMsAvg : Long? = null

    private var _receivedBatchSize : Int? = null
    private var _receivedAt : Date? = null
    private var _lastConsumedAt : Date? = null
    private var _totalReceivedCount = 0
    private var _totalConsumedCount = 0
    private var _totalSkippedCount = 0

    fun createSummary(startedAt: Date) =
        WorkerStats(
            _totalReceivedCount,
            _totalConsumedCount,
            _totalSkippedCount,
            startedAt.diffInMilisecTo(Date())
        )

    private fun getConsumingAvgTimeMs() : Long? {
        if (_consumingTimesMs.size <= 0) {
            return null
        }

        if (_consumingTimeMsAvg == null) {
            val result = _consumingTimesMs.sum() / _consumingTimesMs.size

            if (_consumingTimesMs.size >= _sufficientStatsSize) {
                _consumingTimeMsAvg = result
            }

            return result
        }

        return _consumingTimeMsAvg
    }

    private fun getProducingAvgTimeMs() : Long? {
        if (_producingTimesMs.size <= 0) {
            return null
        }

        if (_producingTimeMsAvg == null) {
            val result = _producingTimesMs.sum() / _producingTimesMs.size

            if (_producingTimesMs.size >= _sufficientStatsSize) {
                _producingTimeMsAvg = result
            }

            return result
        }

        return _producingTimeMsAvg
    }

    fun <T> measureConsumer(func: () -> T) : T {
        val startedAt = Date()
        val result = func()
        val endedAt = Date()

        _totalConsumedCount++
        _lastConsumedAt = Date()

        if (_consumingTimesMs.size < _sufficientStatsSize) {
            _consumingTimesMs.add(startedAt.diffInMilisecTo(endedAt))
        }
        return result
    }

    fun measureProducer(milisecsToProduce:List<Long>) {
        _totalReceivedCount += milisecsToProduce.size

        _receivedAt = Date()
        _receivedBatchSize = milisecsToProduce.size

        if (_producingTimesMs.size >= _sufficientStatsSize) {
            return
        }

        //watch out for firstitem for which time is unknown yet still it should be included in producing rate
        for (milisecToProduce in milisecsToProduce.filter { it > 0 }) {
            _producingTimesMs.add(milisecToProduce)

            if (_producingTimesMs.size >= _sufficientStatsSize) {
                return
            }
        }
    }

    fun skipItemsFollowingSuccess(toSkipCount: Int) {
        _totalSkippedCount += toSkipCount
    }

    fun shouldConsumeItemsCount() : Int? {
        val receivedAt = _receivedAt
        val receivedBatchSize = _receivedBatchSize
        val lastConsumedAt = _lastConsumedAt

        val avgConsumingTimeMs = getConsumingAvgTimeMs()
        val avgProducingTimeMsAvg = getProducingAvgTimeMs()

        if (avgConsumingTimeMs == null || avgProducingTimeMsAvg == null || receivedAt == null || lastConsumedAt == null || receivedBatchSize == null) {
            return null //not possible to estimate anything yet. Wait till have more data for estimation
        }

        val elapsedMilisecSinceBatchStart = receivedAt.diffInMilisecTo(Date())
        val likelyHavePendingItems = Math.ceil(elapsedMilisecSinceBatchStart.toDouble() / avgProducingTimeMsAvg).toInt()
        val timeLeftUntilQueueOverflowMs = (_producerQueueMaxSize - likelyHavePendingItems) * avgProducingTimeMsAvg

        //'floor' would be safest here (producer would get open channel always)
        //'round' depending on value would either cause some starvation or queue overflow
        //using 'ceil' to favor full consumer utilization over queue overflow (we are skipping items anyway likely consuming every 5th photo)
        val timeAllowsToConsumeItemsCount = Math.ceil(timeLeftUntilQueueOverflowMs.toDouble() / avgConsumingTimeMs).toInt()

        val lastConsumeWasAgo = lastConsumedAt.diffInMilisecTo(Date())
        val leftToConsume = _totalReceivedCount - _totalConsumedCount - _totalSkippedCount
        val wouldConsumeItemsCount = Math.min(leftToConsume, timeAllowsToConsumeItemsCount)

        //starving protection
        val forceAtLeastOneToConsume = lastConsumeWasAgo > _consumeAtLeastEveryMs
        val willConsumeItemsCount = if (forceAtLeastOneToConsume) Math.min(1, wouldConsumeItemsCount) else wouldConsumeItemsCount
        val increaseSkipBy = leftToConsume - willConsumeItemsCount

        Timber.i(
            """barcodeDecoderWorker 
receivedBatchSize=$receivedBatchSize received=$_totalReceivedCount consumed=$_totalConsumedCount skipped=$_totalSkippedCount left=$leftToConsume
avgConsuming[ms]=$avgConsumingTimeMs avgProducing[ms]=$avgProducingTimeMsAvg 
timeSinceBatchStart[ms]=$elapsedMilisecSinceBatchStart 
likelyHavePendingItems=$likelyHavePendingItems 
lastConsumeWasAgo[ms]=${lastConsumeWasAgo} 
timeLeftUntilQueueOverflow[ms]=$timeLeftUntilQueueOverflowMs 
timeAllowsToConsumeItemsCount=$timeAllowsToConsumeItemsCount 
forceAtLeastOneToConsume=$forceAtLeastOneToConsume
wouldConsumeItemsCount=$wouldConsumeItemsCount
willConsumeItemsCount=$willConsumeItemsCount
increaseSkipBy=$increaseSkipBy"""
        )

        _totalSkippedCount += increaseSkipBy

        return willConsumeItemsCount
    }
}