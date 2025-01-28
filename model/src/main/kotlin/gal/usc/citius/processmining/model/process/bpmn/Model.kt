package gal.usc.citius.processmining.model.process.bpmn

import gal.usc.citius.processmining.model.Direction
import gal.usc.citius.processmining.model.EdgeType
import gal.usc.citius.processmining.model.process.Activity
import gal.usc.citius.processmining.model.process.ProcessModel

data class BPMN internal constructor(override val id: String, override val activities: Set<BPMNActivity>) :
    ProcessModel {
    override fun toDOT(edges: EdgeType, direction: Direction): String = TODO()
}

data class BPMNActivity(override val id: String, override val name: String) : Activity
