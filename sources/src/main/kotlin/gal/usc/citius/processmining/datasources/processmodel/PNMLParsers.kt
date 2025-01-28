package gal.usc.citius.processmining.datasources.processmodel

import gal.usc.citius.processmining.datasources.FileSource
import gal.usc.citius.processmining.datasources.ReaderSource
import gal.usc.citius.processmining.datasources.createGZIPCompatibleReader
import gal.usc.citius.processmining.model.process.ProcessModel
import gal.usc.citius.processmining.model.process.petrinet.PetriNetBuilder
import org.dom4j.io.SAXReader
import java.io.File
import java.io.Reader
import java.nio.file.Path
import java.nio.file.Paths

class PNMLParser(override val reader: Reader) : ReaderSource<ProcessModel> {

    override fun read(): ProcessModel {
        val petriNet = SAXReader().read(reader).rootElement.element("net")
        val processName = petriNet.element("name").element("text").stringValue
        val petriNetElements = petriNet.element("page")
        val transitions = petriNetElements.elements("transition")
        val places = petriNetElements.elements("place")
        val arcs = petriNetElements.elements("arc")
        val finalMarkings = petriNet.elements("finalmarkings") +
            petriNet.elements("finalMarkings") +
            petriNetElements.elements("finalmarkings") +
            petriNetElements.elements("finalMarkings")
        val markings = finalMarkings.flatMap { it!!.elements("marking") }
        val finalPlaces = markings
            .flatMap { it!!.elements("place") }
            .filter { it.element("text").stringValue.toInt() == 1 }
            .map { it.attribute("idref").value }
            .toSet()

        val builder = PetriNetBuilder.forProcess(processName)

        for (transition in transitions) {
            val name = transition.element("name").element("text").stringValue
            val id = transition.attribute("id").value
            val silent = transition.elements("toolspecific")
                .any {
                    it.attribute("activity")?.value == "\$invisible\$" || (
                        it.attribute("invisible")?.value?.toBoolean()
                            ?: false
                        )
                }
            builder.transition().id(id).name(name).silent(silent).add()
        }

        for (place in places) {
            val name = place.element("name").element("text").stringValue
            val id = place.attribute("id").value
            val initial = place.elements("initialMarking").isNotEmpty()
            val final = id in finalPlaces
            builder.place().id(id).name(name).initial(initial).final(final).add()
        }

        val transitionIds = builder.transitions.map { it.id }.toSet()

        for (arc in arcs) {
            val source = arc.attribute("source").value
            val target = arc.attribute("target").value

            if (source in transitionIds)
                builder.arc().fromTransition(source).to(target).add()
            else
                builder.arc().fromPlace(source).to(target).add()
        }

        return builder.build()
    }
}

class PNMLFileParser(override val file: File) : FileSource<ProcessModel> {
    constructor(filePath: Path) : this(filePath.toFile())
    constructor(filePath: String) : this(Paths.get(filePath))

    override val SUPPORTED_TYPES: Collection<String> = setOf("pnml", "pnml.gz")

    override fun read(): ProcessModel =
        PNMLParser(createGZIPCompatibleReader(file, SUPPORTED_TYPES)).read()
}
