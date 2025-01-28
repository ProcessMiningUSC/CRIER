package gal.usc.citius.processmining.discovery.flower

import gal.usc.citius.processmining.discovery.DiscoveryAlgorithm
import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.process.ProcessModel
import gal.usc.citius.processmining.model.process.petrinet.PetriNetBuilder

class FlowerMiner : DiscoveryAlgorithm {
    override fun discover(log: Log<*>): ProcessModel {
        val petriNetBuilder = PetriNetBuilder.forProcess(log.process)
            .place().id("p0").initial().add()
            .place().id("p1").add()
            .place().id("p2").final().add()
            .transition().id("start").name("start").silent().add()
            .transition().id("end").name("end").silent().add()
            .arc().fromPlace("p0").to("start").add()
            .arc().fromPlace("p1").to("end").add()
            .arc().fromTransition("start").to("p1").add()
            .arc().fromTransition("end").to("p2").add()

        log.activities.forEach {
            petriNetBuilder
                .transition().id(it.id).name(it.name).add()
                .arc().fromPlace("p1").to(it.id).add()
                .arc().fromTransition(it.id).to("p1").add()
        }

        return petriNetBuilder.build()
    }
}
