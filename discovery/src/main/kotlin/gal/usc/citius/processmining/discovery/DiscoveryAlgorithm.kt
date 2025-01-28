package gal.usc.citius.processmining.discovery

import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.process.ProcessModel

interface DiscoveryAlgorithm {
    fun discover(log: Log<*>): ProcessModel
}
