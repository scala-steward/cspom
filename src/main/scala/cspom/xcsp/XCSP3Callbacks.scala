package cspom
package xcsp

import cspom.CSPOM._
import org.xcsp.parser.XCallbacks.{Implem, XCallbacksParameters}
import org.xcsp.parser.XParser
import org.xcsp.parser.entries.XVariables
import org.xcsp.parser.entries.XVariables.XVarInteger


class XCSP3Callbacks extends XCSP3CallbacksObj
  with XCSP3CallbacksGeneric
  with XCSP3CallbacksLanguage
  with XCSP3CallbacksComparison
  with XCSP3CallbacksConnection
  with XCSP3CallbacksCountSum
  with XCSP3CallbacksPackSched {

  val cspom: CSPOM = new CSPOM()

  val implem: Implem = new Implem(this)
  implem.currParameters.put(XCallbacksParameters.CONVERT_INTENSION_TO_EXTENSION_ARITY_LIMIT, Int.box(2))


  def loadInstance(parser: XParser): Unit = {
    beginInstance(parser.typeFramework)
    beginVariables(parser.vEntries)
    loadVariables(parser)
    endVariables()
    beginConstraints(parser.cEntries)
    loadConstraints(parser)
    endConstraints()
    beginObjectives(parser.oEntries, parser.typeCombination)
    loadObjectives(parser)
    endObjectives()
 		beginAnnotations(parser.aEntries)
		loadAnnotations(parser)
		endAnnotations()
    endInstance()
  }




  override def buildCtrInstantiation(id: String, list: Array[XVarInteger], values: Array[Int]): Unit = {
    implicit def problem: CSPOM = cspom

    for ((variable, value) <- (list, values).zipped) {
      cspom.ctr(toCspom(variable) === constant(value))
    }
  }

  override def buildCtrClause(id: String, pos: Array[XVarInteger], neg: Array[XVarInteger]): Unit = {
    cspom.ctr("clause")(toCspom(pos), toCspom(neg))
  }

  override def buildCtrFalse(id: String, list: Array[XVariables.XVar]): Unit = {
    val message = s"Constraint $id(${list.mkString(", ")}) is disentailed"
    logger.warn(message)
    throw new UNSATException(message)
  }
}