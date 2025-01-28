package gal.usc.citius.processmining.conceptdrift

import gal.usc.citius.processmining.conceptdrift.model.DetectionResult
import gal.usc.citius.processmining.conceptdrift.model.DriftPoint

interface ConceptDriftAlgorithm {
    fun run(onDrift: (DriftPoint) -> Any = {}): DetectionResult
}
