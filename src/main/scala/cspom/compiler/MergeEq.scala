package cspom.compiler

import cspom.CSPOM
import cspom.CSPOMConstraint
import cspom.variable.CSPOMConstant
import cspom.variable.CSPOMExpression
import cspom.variable.CSPOMTrue
import cspom.variable.CSPOMVariable


/**
 * If given constraint is an all-equal constraint, merges and removes all
 * auxiliary variables.
 */
object MergeEq extends ConstraintCompiler {

  type A = (List[CSPOMVariable], List[CSPOMVariable], List[CSPOMConstant])

  @annotation.tailrec
  private def partition(
    s: List[CSPOMExpression],
    aux: List[CSPOMVariable] = Nil,
    full: List[CSPOMVariable] = Nil,
    const: List[CSPOMConstant] = Nil): (List[CSPOMVariable], List[CSPOMVariable], List[CSPOMConstant]) =
    s match {
      case Nil => (aux, full, const)
      case (a: CSPOMVariable) :: tail if a.params("var_is_introduced") => partition(tail, a :: aux, full, const)
      case (f: CSPOMVariable) :: tail => partition(tail, aux, f :: full, const)
      case (c: CSPOMConstant) :: tail => partition(tail, aux, full, c :: const)
      case o => throw new IllegalArgumentException(o.toString)
    }

  override def matchConstraint(c: CSPOMConstraint) = c match {
    case CSPOMConstraint(CSPOMTrue, 'eq, args, params) if !params.contains("neg") && params.get("offset").forall(_ == 0) =>
      val (aux, full, const) = partition(c.arguments.toList)
      if (aux.nonEmpty) {
        Some((aux, full, const))
      } else None

    case _ => None

  }

  private def mergeConstant(
    fullVars: Seq[CSPOMVariable],
    auxVars: Seq[CSPOMVariable],
    constant: CSPOMConstant, problem: CSPOM): Delta = {

    val delta = fullVars.foldLeft(Delta()) {
      case (delta, v) =>
        val nv = v.intersected(constant)
        delta ++ replace(Seq(v), nv, problem)
    }

    delta ++ replace(auxVars, constant, problem)

  }

  private def mergeVariables(
    fullVars: Seq[CSPOMVariable],
    auxVars: Seq[CSPOMVariable],
    problem: CSPOM): Delta = {
    var delta = Delta()

    val merged = (fullVars ++ auxVars).reduceLeft[CSPOMExpression](_ intersected _)

    /**
     * Tighten fullVars' domain
     */

    val newFullVars = for (v <- fullVars) yield {
      val nv = v.intersected(merged)
      delta ++= replace(Seq(v), nv, problem)
      nv
    }

    /**
     * Replacing aux variables by a single one (full var if available)
     */
    val refVar = newFullVars.headOption.getOrElse(auxVars.head)

    delta ++ replace(auxVars.distinct, refVar, problem)
  }

  def compile(constraint: CSPOMConstraint, problem: CSPOM, data: A) = {
    val (auxVars, fullVars, const) = data

    problem.removeConstraint(constraint)

    val delta = Delta().removed(constraint)
    /**
     * Generate a new all-equal constraint if more than one variable
     * remains.
     */
    if (fullVars.size > 1) {
      problem.ctr(new CSPOMConstraint('eq, fullVars.toSeq: _*))
    }

    val oldNames = problem.namedExpressions.keySet

    /**
     * Update the constraints of the problem
     */
    val delta2 = delta ++ (
      if (const.isEmpty) {
        mergeVariables(fullVars, auxVars, problem)
      } else {
        val (c :: tail) = const
        require(tail.forall(_ == c), "Inconsistent constants in " + (c :: tail))
        mergeConstant(fullVars, auxVars, c, problem)
      })

    require(oldNames == problem.namedExpressions.keySet,
      s"Variables from $oldNames were removed")

    delta2

  }
  
  def selfPropagation = false

}