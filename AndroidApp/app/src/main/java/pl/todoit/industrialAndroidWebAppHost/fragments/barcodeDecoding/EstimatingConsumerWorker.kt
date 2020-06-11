package pl.todoit.industrialAndroidWebAppHost.fragments.barcodeDecoding

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import pl.todoit.industrialAndroidWebAppHost.App
import pl.todoit.industrialAndroidWebAppHost.model.Result
import pl.todoit.industrialAndroidWebAppHost.model.extensions.*
import timber.log.Timber
import java.io.Closeable
import java.util.*

fun asClosable(vararg items : Closeable) = Closeable { items.forEach { it.close() } }

/**
 * Consumer worker that consumes as many items as CPUs are able to process WHILE striving to not let producer be blocked.
 * Assumptions: producer rate and consumer consuming speed are +- stable
 *
 * @param prioritize sorts items to process according to expected usefulness from most useful to less useful
 * @param isResetStatisticsRequest consuming process may be paused externally due to different factors. Need to account it
 */
class EstimatingConsumerWorker<InputT : Any,OutputT>(
    val consumerFactory : ()->IItemConsumer<InputT,OutputT>,
    private val prioritize:(List<InputT>)->List<InputT>,
    private val maxSimultaneousComputations : Int,
    val isResetStatisticsRequest : (InputT)->Boolean,
    val extractTimeToProduceMs : (InputT)->Int
) {
    private val _input = Channel<InputT>(App.Instance.imagesToDecodeQueueSize)
    private val _output = Channel<ProcessorSuccess<OutputT>>()
    private var _stats = WorkerEstimator(maxSimultaneousComputations)
    private val _processors = (0 until maxSimultaneousComputations).map { consumerFactory() }

    fun toConsume() : SendChannel<InputT> = _input
    fun consumed() : ReceiveChannel<ProcessorSuccess<OutputT>> = _output
    fun clearToConsume() = App.Instance.launchCoroutine { _input.receiveAllPending().clear() }

    suspend fun startConsumerWorker() {
        Timber.i("started with parallelism=$maxSimultaneousComputations")

        val toDoBatch = Channel<Pair<IItemConsumer<InputT,OutputT>,InputT>>(maxSimultaneousComputations)
        val decodedWithMillis = Channel<Pair<Result<OutputT,Exception>,Int>>(maxSimultaneousComputations)

        asClosable(_output.toCloseable(), toDoBatch.toCloseable(), decodedWithMillis.toCloseable()).use {
            //workers will be auto closed once scope ends
            (0 until maxSimultaneousComputations).forEach {
                App.Instance.launchParallelInBackground {
                    Timber.d("worker starting worker id=$it")
                    for ((processor, img) in toDoBatch) {
                        val started = Date()
                        val resultCode = processor.process(img)
                        val tookMs = started.diffInMilisecTo(Date())
                        decodedWithMillis.send(Pair(resultCode, tookMs.toInt()))
                    }
                    Timber.d("startDecoderWorker ending worker id=$it")
                }
            }

            while (!_input.isClosedForReceive) {
                var toDecodes = prioritize(_input.receiveAllPending()).toMutableList()
                Timber.d("worker time=${Date().time} received ${toDecodes.size} items")

                if (toDecodes.any { isResetStatisticsRequest(it) }) {
                    Timber.d("worker got resetStats request")
                    _stats = WorkerEstimator(maxSimultaneousComputations)
                }

                _stats.measureProducer(toDecodes.map { extractTimeToProduceMs(it) })

                var estimationMade = false

                while (toDecodes.size > 0) {
                    _stats.batchConsumeStarts()

                    if (!estimationMade) {
                        val shouldConsumeItemsCount = _stats.shouldConsumeItemsCount()
                        if (shouldConsumeItemsCount != null) {
                            estimationMade = true

                            val oldSize = toDecodes.size
                            toDecodes = toDecodes.subList(0, shouldConsumeItemsCount)
                            Timber.d("items list shortened from $oldSize to ${toDecodes.size} items")

                            if (toDecodes.size <= 0) {
                                continue
                            }
                        }
                    }

                    val batch = toDecodes.popAtMostFirstItems(maxSimultaneousComputations)
                    Timber.d("will now process ${batch.size} items in parallel")

                    batch
                        .zip(_processors)
                        .forEach { (img, mfr) -> toDoBatch.send(Pair(mfr, img)) }

                    val workerAnswers = decodedWithMillis.receiveExactly(batch.size)
                    workerAnswers.forEach { _stats.consumerMeasurementRegister(it.second) }

                    _stats.batchConsumeEnds()

                    val maybeAnswer = workerAnswers
                        .map {
                            when(val x = it.first) {
                                is Result.Ok -> x.value
                                is Result.Error -> null
                        }  }
                        .filter { it != null }
                        .firstOrNull()

                    if (maybeAnswer != null) {
                        //no need to process the reminder & should also help GC

                        _input.receiveAllPending() //purge queue to ease GC
                        _stats.ignoreItemsFollowingSuccess(toDecodes.size)
                        toDecodes.clear() //no need to decode the rest

                        _output.send(
                            ProcessorSuccess(maybeAnswer, _stats.createSummary()))
                    }
                }

                Timber.d("time=${Date().time} finished with current batch")
            }
            Timber.d("ending")
        }
    }

    fun endConsumerWorker() = _input.close()
}
