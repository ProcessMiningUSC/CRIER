package gal.usc.citius.processmining.model.process.processtree

class BadProcessTreeSyntaxException(process: String) : Error("The specified tree is invalid: $process")
class UnknownOperatorException(operator: String) : Error("Unknown process tree operator: $operator")
