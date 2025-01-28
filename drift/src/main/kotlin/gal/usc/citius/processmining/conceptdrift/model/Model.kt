package gal.usc.citius.processmining.conceptdrift.model

import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.log.Trace
import gal.usc.citius.processmining.model.process.ProcessModel
import org.apache.commons.math3.stat.regression.SimpleRegression

enum class DriftType {
    SUDDEN,
    GRADUAL,
    UNKNOWN
}

data class DriftPoint(
    val type: DriftType,
    val cause: DriftCause,
    val location: IntRange,
    val model: ProcessModel,
    val previous: DriftContext,
    val posterior: DriftContext
)

class Window<T>(val size: Int) {
    val content: List<T> get() = items.toList()
    val full: Boolean get() = items.size == size
    val empty: Boolean get() = items.isEmpty()
    val last: T get() = items.last()
    val first: T get() = items.first()

    companion object {
        fun <T> from(content: Collection<T>): Window<T> {
            val w = Window<T>(content.size)
            w.add(content)

            return w
        }
    }

    private val items = mutableListOf<T>()

    fun clear(): Window<T> {
        this.items.clear()
        return this
    }

    fun add(content: Collection<T>): Window<T> {
        for (item in content)
            this.add(item)
        return this
    }

    fun add(item: T): Window<T> {
        if (items.size < size)
            items.add(item)
        else {
            items.removeAt(0)
            items.add(item)
        }
        return this
    }

    override fun toString(): String {
        return items.toString()
    }
}

class SlidingLog private constructor(val log: Log<*>, private var size: Int) {
    companion object {
        fun from(log: Log<*>, size: Int): SlidingLog = SlidingLog(log, size)
    }

    private var windows: List<Log<Trace<*>>> = log.traces.windowed(size, 1, false).map {
        Log(
            process = log.process,
            traces = it
        )
    }

    private var currentWindowIndex = 0
    val currentWindow get() = windows[currentWindowIndex]
    val lastTrace get() = currentWindow.traces.last()
    val lastTraceIndex get() = currentWindowIndex + size
    val firstTrace get() = currentWindow.traces.first()
    val firstTraceIndex get() = currentWindowIndex
    val windowLimits get() = WindowLimits(firstTraceIndex, lastTraceIndex)

    fun canSlideForward(count: Int = 1) = (currentWindowIndex + count) < windows.size
    fun canSlideBackward(count: Int = 1) = (currentWindowIndex - count) >= 0
    fun slideForward(count: Int = 1): Log<Trace<*>> {
        if ((currentWindowIndex + count) > windows.size)
            throw Error("Can not advance $count forward")

        currentWindowIndex += count
        return windows[currentWindowIndex]
    }

    fun slideBackwards(count: Int = 1): Log<Trace<*>> {
        if ((currentWindowIndex - count) < 0)
            throw Error("Can not advance $count back")

        currentWindowIndex -= count

        return windows[currentWindowIndex]
    }

    fun slideToEnd(): Log<Trace<*>> {
        currentWindowIndex = windows.lastIndex

        return windows[currentWindowIndex]
    }

    fun slideToStart(): Log<Trace<*>> {
        currentWindowIndex = 0

        return windows[currentWindowIndex]
    }

    fun reslice(newWindowSize: Int): SlidingLog {
        if (this.size != newWindowSize) {
            this.size = newWindowSize
            this.windows = log.traces.windowed(newWindowSize, 1, false).map {
                Log(
                    process = log.process,
                    traces = it
                )
            }
        }

        return this
    }
}

data class ModelWithData(
    val model: ProcessModel,
    val data: Window<Trace<*>>,
    val fitnessRegression: SimpleRegression = SimpleRegression(),
    val precisionRegression: SimpleRegression = SimpleRegression(),
    val fitnessMeasurements: Window<IndexedMeasurement>,
    val precisionMeasurements: Window<IndexedMeasurement>,
    val drifts: Window<IndexedDrift>
) {
    fun updateTraces(log: Log<Trace<*>>) {
        data.clear().add(log.traces)
    }

    fun addFitness(index: Int, value: Double) {
        fitnessMeasurements.add(IndexedMeasurement(index, value))

        if (fitnessMeasurements.full) {
            fitnessRegression.clear()
            fitnessRegression.addData(
                fitnessMeasurements.content.map {
                    doubleArrayOf(
                        it.index.toDouble(),
                        it.measurement
                    )
                }.toTypedArray()
            )
        }
    }

    fun addPrecision(index: Int, value: Double) {
        precisionMeasurements.add(IndexedMeasurement(index, value))

        if (precisionMeasurements.full) {
            precisionRegression.clear()
            precisionRegression.addData(
                precisionMeasurements.content.map {
                    doubleArrayOf(
                        it.index.toDouble(),
                        it.measurement
                    )
                }.toTypedArray()
            )
        }
    }
}

data class IndexedDrift(val index: Int, val fitnessDrift: Boolean, val precisionDrift: Boolean) {
    val cause: DriftCause
        get() = when {
            this.fitnessDrift && this.precisionDrift -> DriftCause.BOTH
            this.fitnessDrift -> DriftCause.FITNESS
            this.precisionDrift -> DriftCause.PRECISION
            else -> DriftCause.UNKNOWN
        }
}

data class IndexedMeasurement(val index: Int, val measurement: Double)
data class WindowLimits(val from: Int, val to: Int)
data class DriftContext(val model: ProcessModel)
data class DetectionResult(val drifts: List<DriftPoint>, val fitness: List<Double>, val precision: List<Double>)

enum class DriftCause {
    UNKNOWN, FITNESS, PRECISION, BOTH
}
