package gal.usc.citius.processmining.model.process.petrinet

class TransitionNotFoundError(transition: String) : Error("Transition $transition not found in petri net!")
class PlaceNotFoundError(place: String) : Error("Place $place not found in petri net!")
