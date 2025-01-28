package gal.usc.citius.processmining.datasources.processmodel

import gal.usc.citius.processmining.datasources.ReaderSource
import gal.usc.citius.processmining.model.process.causal.CausalActivity
import gal.usc.citius.processmining.model.process.causal.CausalConnections
import gal.usc.citius.processmining.model.process.causal.CausalModel
import java.io.Reader

abstract class CausalModelParser<A : CausalActivity>(
    override val reader: Reader,
    private val modelId: String = "CausalModel"
) : ReaderSource<CausalModel<A>> {
    protected companion object {
        const val SECTION_SEPARATOR = "/////////////////////"

        enum class ParsingState {
            INITIALIZING,
            START_ACTIVITY,
            END_ACTIVITY,
            ACTIVITIES,
            CONNECTIONS
        }
    }

    protected abstract val SET_SEPARATOR: String
    protected abstract val IDS_SEPARATOR: String

    override fun read(): CausalModel<A> =
        reader.useLines { lines ->
            val activityNamesMapping: MutableMap<String, String> = mutableMapOf() // Mapping<activityId, activityName>
            val activities: MutableMap<String, A> = mutableMapOf()
            var parsingState = ParsingState.INITIALIZING
            lines.forEach { line ->
                when (parsingState) {
                    ParsingState.INITIALIZING ->
                        if (line == SECTION_SEPARATOR) {
                            parsingState = ParsingState.START_ACTIVITY
                        }

                    ParsingState.START_ACTIVITY ->
                        if (line == SECTION_SEPARATOR) {
                            parsingState = ParsingState.END_ACTIVITY
                        }

                    ParsingState.END_ACTIVITY ->
                        if (line == SECTION_SEPARATOR) {
                            parsingState = ParsingState.ACTIVITIES
                        }

                    ParsingState.ACTIVITIES ->
                        if (line == SECTION_SEPARATOR) {
                            parsingState = ParsingState.CONNECTIONS
                        } else if (line.isNotBlank()) {
                            activityNamesMapping += lineToActivity(line)
                        }

                    ParsingState.CONNECTIONS ->
                        if (line.isNotBlank()) {
                            val (intId, inputs, outputs) = line.split("@")
                            val activityName = activityNamesMapping[intId]!!
                            // Update empty activity with the connections
                            activities[intId] = createCausalActivity(
                                id = intId,
                                name = activityName,
                                inputs = stringToConnections(inputs),
                                outputs = stringToConnections(outputs)
                            )
                        }
                }
            }

            createCausalModel(modelId, activities.values.toSet())
        }

    /**
     * Transform a line into a pair with the id as first element, and the name as second.
     *
     * @param line a string with format: <activity_name>@<activity_id>&
     *
     * @return a [Pair] with the id of the activity as [Pair.first] and its name as [Pair.second].
     */
    private fun lineToActivity(line: String): Pair<String, String> {
        val (name, id) = line.dropLast(1).split("@")
        return (id to name)
    }

    /**
     * Transform a string into [CausalConnections] with the identifiers of the connections.
     *
     * @param connections a string with the connection ids separated by [SET_SEPARATOR] and [IDS_SEPARATOR], for
     * instance: 1&2&3|4|5&6
     *
     * @return an instance of [CausalConnections] with the ids from [connections].
     */
    private fun stringToConnections(connections: String): CausalConnections =
        if (connections == ".")
            emptySet()
        else
            connections
                .split(SET_SEPARATOR)
                .map { subset ->
                    subset
                        .split(IDS_SEPARATOR)
                        .toSet()
                }
                .toSet()

    /**
     * Create an instance of a subclass of [CausalModel] with activities of class [A].
     *
     * @param id the identifier of the model.
     * @param activities the set of activities of class [A] of the model to create.
     *
     * @return the instance of the subclass of [CausalModel] with activities of [A].
     */
    protected abstract fun createCausalModel(
        id: String,
        activities: Set<A>
    ): CausalModel<A>

    /**
     * Return an instance of an activity with class [A].
     *
     * @param id the identifier of the activity.
     * @param name the name of the activity.
     * @param inputs input connections of the activity.
     * @param outputs output connections of the activity.
     *
     * @return the instance of [A] with the values passed.
     */
    abstract fun createCausalActivity(
        id: String,
        name: String,
        inputs: CausalConnections,
        outputs: CausalConnections
    ): A
}
