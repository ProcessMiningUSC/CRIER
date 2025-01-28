package gal.usc.citius.processmining.conceptdrift.structural

import gal.usc.citius.processmining.conceptdrift.ConceptDriftAlgorithm
import gal.usc.citius.processmining.conceptdrift.model.DetectionResult
import gal.usc.citius.processmining.conceptdrift.model.DriftCause
import gal.usc.citius.processmining.conceptdrift.model.DriftContext
import gal.usc.citius.processmining.conceptdrift.model.DriftPoint
import gal.usc.citius.processmining.conceptdrift.model.DriftType
import gal.usc.citius.processmining.conceptdrift.model.IndexedDrift
import gal.usc.citius.processmining.conceptdrift.model.ModelWithData
import gal.usc.citius.processmining.conceptdrift.model.SlidingLog
import gal.usc.citius.processmining.conceptdrift.model.Window
import gal.usc.citius.processmining.conceptdrift.model.WindowLimits
import gal.usc.citius.processmining.discovery.DiscoveryAlgorithm
import gal.usc.citius.processmining.metrics.FitnessMetric
import gal.usc.citius.processmining.metrics.PrecisionMetric
import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.log.Trace
import gal.usc.citius.processmining.utils.analytics.booleanReplay
import gal.usc.citius.processmining.utils.sampling.subsampleTraces
import mu.KotlinLogging
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.system.measureTimeMillis

private val LOGGER = KotlinLogging.logger {}

class Algorithm(
    private val originalLog: Log<*>,
    private val windowSizeOptimizer: (Log<*>) -> Int,
    private val step: Int = 1,
    private val discoveryAlgorithm: DiscoveryAlgorithm,
    private val fitnessMetric: FitnessMetric,
    private val precisionMetric: PrecisionMetric,
    private val significance: Double = 0.05,
    private val measurementsToConsider: Double = 0.5,
    private val minDriftsToConfirm: Double = 1.0,
    private val classifyResults: Boolean = true
) : ConceptDriftAlgorithm {
    override fun run(onDrift: (data: DriftPoint) -> Any): DetectionResult {
        // The list of drift points resulting from executing the algorithm, classified in gradual and sudden
        lateinit var classifiedDrifts: List<DriftPoint>

        // Used only for plotting the final results
        val fitnessHistory = mutableListOf<Double>()
        val precisionHistory = mutableListOf<Double>()

        measureTimeMillis {
            val drifts: MutableList<DriftPoint> = mutableListOf()
            val windowSize = windowSizeOptimizer(originalLog)

            val log = SlidingLog.from(originalLog, windowSize)

            LOGGER.info { "Running concept drift detection algorithm with the following parameters:" }
            LOGGER.info { "    * Log source                : ${originalLog.source}" }
            LOGGER.info { "    * Process name              : ${originalLog.process}" }
            LOGGER.info { "    * Window size               : $windowSize" }
            LOGGER.info { "    * Step                      : $step" }
            LOGGER.info { "    * Discovery algorithm       : ${discoveryAlgorithm::class.qualifiedName}" }
            LOGGER.info { "    * Fitness metric            : ${fitnessMetric::class.qualifiedName}" }
            LOGGER.info { "    * Precision metric          : ${precisionMetric::class.qualifiedName}" }
            LOGGER.trace { "Entering initialization phase." }

            while (log.canSlideForward(step)) {
                // Compute models for the clusters of the initial window
                LOGGER.trace { "Discovering the initial model." }
                val model: ModelWithData = log.currentWindow.discoverModel(
                    discoveryAlgorithm,
                    measurementsToConsider,
                    minDriftsToConfirm
                )

                // Compute initial metrics for each cluster
                LOGGER.trace { "Computing initial metrics." }
                model.updateMetrics(
                    fitnessMetric,
                    precisionMetric,
                    log.windowLimits,
                    fitnessHistory,
                    precisionHistory
                )

                // While the log is not empty...
                LOGGER.trace { "Initialization phase done." }
                LOGGER.trace { "Entering detection phase." }

                while (log.canSlideForward(step)) {
                    // Read a new trace
                    log.slideForward(step)

                    model.updateTraces(log.currentWindow)
                    LOGGER.trace { "--------------------------------" }
                    LOGGER.trace { "Processing trace ${log.lastTraceIndex}" }

                    // Update metrics
                    LOGGER.trace { "Updating fitness and precision measurements for the model" }
                    model.updateMetrics(
                        fitnessMetric,
                        precisionMetric,
                        log.windowLimits,
                        fitnessHistory,
                        precisionHistory
                    )
                    LOGGER.trace { "Checking for drifts in the model" }

                    model.updateDrifts(log.windowLimits, significance)

                    if (model.hasConfirmedDrift()) {
                        LOGGER.info { "Detected drift on trace ${model.getDrift().index}" }

                        val driftPoint = DriftPoint(
                            type = DriftType.UNKNOWN,
                            cause = when {
                                model.hasConfirmedFitnessDrift() -> DriftCause.FITNESS
                                model.hasConfirmedPrecisionDrift() -> DriftCause.PRECISION
                                else -> DriftCause.UNKNOWN
                            },
                            location = (model.getDrift().index..model.getDrift().index),
                            model = model.model,
                            previous = DriftContext(model = model.model),
                            posterior = DriftContext(
                                model = discoveryAlgorithm.discover(
                                    originalLog.subsampleTraces(
                                        from = model.getDrift().index,
                                        to = model.getDrift().index + windowSize
                                    )
                                )
                            )
                        )

                        drifts.add(driftPoint)

                        // Execute user-defined listener for the drift
                        onDrift(driftPoint)
                        log.reslice(
                            windowSizeOptimizer(
                                originalLog.subsampleTraces(
                                    log.firstTraceIndex,
                                    originalLog.size
                                )
                            )
                        )
                        break
                    }
                }
            }

            LOGGER.trace { "Finished detection phase" }

            classifiedDrifts = when (classifyResults) {
                true -> {
                    LOGGER.trace { "Classifying drifts..." }
                    val result = drifts.classify(originalLog, discoveryAlgorithm)
                    LOGGER.trace { "Finished classification phase" }

                    result
                }
                false -> drifts
            }
        }.run {
            LOGGER.info { "Drift detection results:" }
            for (drift in classifiedDrifts) {
                LOGGER.info { "    * ${drift.type} ${drift.location} (${drift.cause})" }
            }
            LOGGER.info { "Execution took $this ms" }
        }

        return DetectionResult(classifiedDrifts, fitnessHistory, precisionHistory)
    }
}

private fun List<DriftPoint>.classify(log: Log<*>, miner: DiscoveryAlgorithm): List<DriftPoint> {
    return if (this.size < 2) {
        this.map { it.copy(type = DriftType.SUDDEN) }
    } else {
        val drifts = mutableListOf<DriftPoint>()
        var skipOne = false

        for (i in 1 until this.size) {
            val prevModel = this[i - 1].model
            val postModel = if (i + 1 <= this.lastIndex) this[i + 1].model else miner.discover(
                log.subsampleTraces(
                    from = (this[i].location.last + 1),
                    to = (log.size - 1)
                )
            )
            val logBetweenChanges = log.subsampleTraces(this[i - 1].location.first, this[i].location.last)

            val tracesFitness = logBetweenChanges.traces.map {
                val pre = prevModel.booleanReplay(it)
                val post = postModel.booleanReplay(it)

                Triple(pre, post, pre || post)
            }

            when {
                skipOne -> skipOne = false

                (this[i - 1].cause == DriftCause.FITNESS && this[i].cause == DriftCause.PRECISION) &&
                    (tracesFitness.any { it.first } && tracesFitness.any { it.second } && tracesFitness.all { it.third }) -> {
                    drifts.add(
                        DriftPoint(
                            type = DriftType.GRADUAL,
                            cause = DriftCause.BOTH,
                            location = this[i - 1].location.first..this[i].location.last,
                            model = this[i].model,
                            previous = this[i - 1].previous,
                            posterior = this[i].posterior
                        )
                    )
                    skipOne = true
                }

                else -> {
                    drifts.add(
                        DriftPoint(
                            type = DriftType.SUDDEN,
                            cause = this[i - 1].cause,
                            location = this[i - 1].location,
                            model = this[i - 1].model,
                            previous = this[i - 1].previous,
                            posterior = this[i - 1].posterior,
                        )
                    )
                }
            }
        }

        drifts
    }
}

private fun Log<Trace<*>>.discoverModel(
    algorithm: DiscoveryAlgorithm,
    measurementsToConsider: Double,
    minDriftsToConfirm: Double
): ModelWithData {
    LOGGER.trace { "Discovery details:" }
    LOGGER.trace { "    * Discoverer      : ${algorithm::class}" }

    val model = algorithm.discover(this)

    return ModelWithData(
        model = model,
        data = Window.from(this.traces),
        fitnessMeasurements = Window((this.size * measurementsToConsider).roundToInt()),
        precisionMeasurements = Window((this.size * measurementsToConsider).roundToInt()),
        drifts = Window((this.size * minDriftsToConfirm).roundToInt())
    )
}

private fun ModelWithData.updateMetrics(
    fm: FitnessMetric,
    pm: PrecisionMetric,
    indices: WindowLimits,
    fitnessHistory: MutableList<Double>,
    precisionHistory: MutableList<Double>
) {
    LOGGER.trace { "Computed metrics:" }

    val computedFitness = fm.compute(this.model, this.data.content)
    val computedPrecision = pm.compute(this.model, this.data.content)

    fitnessHistory.add(computedFitness)
    precisionHistory.add(computedPrecision)

    LOGGER.trace { "\tFitness   = $computedFitness" }
    LOGGER.trace { "\tPrecision = $computedPrecision" }

    this.addFitness(indices.to, computedFitness)
    this.addPrecision(indices.to, computedPrecision)
}

private fun ModelWithData.updateDrifts(limits: WindowLimits, significance: Double) {
    LOGGER.trace { "Drift detection result:" }

    this.drifts.add(
        IndexedDrift(
            index = when {
                this.hasPrecisionDrift(significance) -> limits.from
                else -> limits.to
            },
            fitnessDrift = this.hasFitnessDrift(significance),
            precisionDrift = this.hasPrecisionDrift(significance)
        )
    )

    LOGGER.trace { "\t${this.drifts.last}" }
}

private fun ModelWithData.hasFitnessDrift(significance: Double): Boolean = and(
    this.fitnessMeasurements.full,
    or(
        and(
            this.fitnessRegression.significance < significance,
            this.fitnessRegression.slope.sign == Double.NEGATIVE_INFINITY.sign
        ),
        and(
            (!this.drifts.empty) && this.drifts.last.fitnessDrift,
            !and(
                this.fitnessRegression.significance < significance,
                this.fitnessRegression.slope.sign == Double.POSITIVE_INFINITY.sign
            )
        )
    )
)

private fun ModelWithData.hasPrecisionDrift(significance: Double): Boolean = and(
    this.precisionMeasurements.full,
    or(
        and(
            this.precisionRegression.significance < significance,
            this.precisionRegression.slope.sign == Double.NEGATIVE_INFINITY.sign
        ),
        and(
            (!this.drifts.empty) && this.drifts.last.precisionDrift,
            !and(
                this.precisionRegression.significance < significance,
                this.precisionRegression.slope.sign == Double.POSITIVE_INFINITY.sign
            )
        )
    )
)

private fun ModelWithData.getDrift(): IndexedDrift = this.drifts.first

private fun ModelWithData.hasConfirmedDrift(): Boolean =
    this.hasConfirmedFitnessDrift() || this.hasConfirmedPrecisionDrift()

private fun ModelWithData.hasConfirmedFitnessDrift(): Boolean =
    this.drifts.full && this.drifts.content.all { it.fitnessDrift }

private fun ModelWithData.hasConfirmedPrecisionDrift(): Boolean =
    this.drifts.full && this.drifts.content.all { it.precisionDrift }

private fun and(vararg conditions: Boolean): Boolean = conditions.all { it }
private fun or(vararg conditions: Boolean): Boolean = conditions.any { it }
