package cspom.flatzinc

import cspom.variable.BoolVariable
import cspom.variable.CSPOMExpression
import cspom.variable.CSPOMSeq
import cspom.variable.CSPOMVariable

sealed trait FZVarType {
  def genVariable(name: String, ann: Set[String]): CSPOMExpression
}

object FZBoolean extends FZVarType {
  def genVariable(name: String, ann: Set[String]) = new BoolVariable(name, ann)
}

object FZFloat extends FZVarType {
  def genVariable(name: String, ann: Set[String]) = ???
}

final case class FZFloatInterval(lb: Double, ub: Double) extends FZVarType {
  def genVariable(name: String, ann: Set[String]) = ???
}

object FZInt extends FZVarType {
  def genVariable(name: String, ann: Set[String]) = ???
}

final case class FZIntInterval(lb: Int, ub: Int) extends FZVarType {
  def genVariable(name: String, ann: Set[String]) = CSPOMVariable.ofInterval(name, lb, ub)
}

final case class FZIntSeq(values: Seq[Int]) extends FZVarType {
  def genVariable(name: String, ann: Set[String]) = CSPOMVariable.ofIntSeq(name, values, ann)
}

final case class FZArray(indices: Range, typ: FZVarType) extends FZVarType {
  def genVariable(name: String, ann: Set[String]) = new CSPOMSeq(
    name,
    indices.map(i => typ.genVariable(s"$name[$i]", Set())),
    indices,
    ann)

}