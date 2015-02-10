package cspom.compiler

import cspom.CSPOMConstraint
import cspom.variable.CSPOMConstant
import cspom.CSPOM
import cspom.variable.CSPOMVariable
import cspom.variable.CSPOMExpression
import cspom.variable.CSPOMSeq
import cspom.flatzinc.FZAnnotation
import cspom.flatzinc.FZVarParId
import com.typesafe.scalalogging.LazyLogging
import cspom.util.ContiguousIntRangeSet
import cspom.variable.SimpleExpression
import cspom.variable.IntVariable
import cspom.util.RangeSet
import cspom.util.Infinitable
import cspom.variable.IntExpression
import cspom.util.Interval

trait ConstraintCompiler extends LazyLogging {
  type A

  def mtch(c: CSPOMConstraint[_], p: CSPOM): Option[A] = matcher.lift((c, p)) orElse matchConstraint(c)

  def matcher: PartialFunction[(CSPOMConstraint[_], CSPOM), A] = PartialFunction.empty

  def matchConstraint(c: CSPOMConstraint[_]) = constraintMatcher.lift(c)

  def constraintMatcher: PartialFunction[CSPOMConstraint[_], A] = PartialFunction.empty

  def compile(constraint: CSPOMConstraint[_], problem: CSPOM, matchData: A): Delta

  def replace[T, S <: T](wh: Seq[CSPOMExpression[T]], by: CSPOMExpression[S], in: CSPOM): Delta = {
    //println(s"Replacing $which with $by")

    val which = wh.filter(_ ne by)

    which.foreach(in.replaceExpression(_, by))

    val oldConstraints = which.flatMap(in.constraints).distinct

    val newConstraints = for (c <- oldConstraints) yield {
      which.foldLeft[CSPOMConstraint[_]](c) { (c, v) =>
        c.replacedVar(v, by)
      }
    }

    logger.debug("Replacing " + oldConstraints + " with " + newConstraints)

    replaceCtr(oldConstraints, newConstraints, in)
  }

  def replaceCtr(which: CSPOMConstraint[_], by: CSPOMConstraint[_], in: CSPOM): Delta = {
    in.removeConstraint(which)
    in.ctr(by)
    Delta().removed(which).added(by)
  }

  def replaceCtr(which: Seq[CSPOMConstraint[_]], by: CSPOMConstraint[_], in: CSPOM): Delta = {
    which.foreach(in.removeConstraint)
    val d = Delta().removed(which)
    in.ctr(by)
    d.added(by)
  }

  def replaceCtr(which: CSPOMConstraint[_], by: Seq[CSPOMConstraint[_]], in: CSPOM): Delta = {
    replaceCtr(Seq(which), by, in)
  }

  def replaceCtr(which: Seq[CSPOMConstraint[_]], by: Seq[CSPOMConstraint[_]], in: CSPOM): Delta = {
    which.foreach(in.removeConstraint)
    val dr = Delta().removed(which)

    by.foreach(in.ctr(_))

    dr.added(by)
  }

  def selfPropagation: Boolean

  def reduceDomain(v: SimpleExpression[Int], d: Interval[Infinitable]): SimpleExpression[Int] = reduceDomain(v, RangeSet(d))

  def reduceDomain(v: SimpleExpression[Int], d: RangeSet[Infinitable]): SimpleExpression[Int] = {
    val old = IntExpression.implicits.ranges(v)
    val reduced = old & d
    if (old == reduced) {
      v
    } else {
      new ContiguousIntRangeSet(reduced).singletonMatch match {
        case Some(s) => CSPOMConstant(s, v.params)
        case None    => IntVariable(reduced, v.params)
      }
    }
  }

  def applyDomain(v: SimpleExpression[Int], reduced: RangeSet[Infinitable]): SimpleExpression[Int] = {
    val old = IntExpression.implicits.ranges(v)
    if (old == reduced) {
      v
    } else {
      new ContiguousIntRangeSet(reduced).singletonMatch match {
        case Some(s) => CSPOMConstant(s, v.params)
        case None    => IntVariable(reduced, v.params)
      }
    }
  }

  def reduceDomain(v: SimpleExpression[Boolean], d: Boolean): SimpleExpression[Boolean] = {
    v match {
      case b: CSPOMVariable[_] => CSPOMConstant(d, b.params)
      case c @ CSPOMConstant(b) =>
        require(b == d, s"Reduced $v to $d: empty domain")
        c
    }
  }
}

trait ConstraintCompilerNoData extends ConstraintCompiler {
  type A = Unit
  def matchBool(constraint: CSPOMConstraint[_], problem: CSPOM): Boolean

  override def mtch(constraint: CSPOMConstraint[_], problem: CSPOM) =
    if (matchBool(constraint, problem)) Some(())
    else None

  def compile(constraint: CSPOMConstraint[_], problem: CSPOM): Delta
  def compile(constraint: CSPOMConstraint[_], problem: CSPOM, matchData: Unit) = compile(constraint, problem: CSPOM)
}

final case class Delta private (
  removed: Seq[CSPOMConstraint[_]],
  added: Seq[CSPOMConstraint[_]]) {
  def removed(c: CSPOMConstraint[_]): Delta = {
    Delta(c +: removed, added.filter(_ ne c))
  }

  def removed(c: Traversable[CSPOMConstraint[_]]): Delta = {
    val cset = c.toSet
    Delta(c ++: removed, added.filterNot(cset))
  }

  def added(c: CSPOMConstraint[_]): Delta = {
    require(!removed.contains(c))
    Delta(removed, c +: added)
  }

  def added(c: Traversable[CSPOMConstraint[_]]): Delta = {
    require(c.forall(!removed.contains(_)))
    Delta(removed, c ++: added)
  }

  def ++(d: Delta): Delta = removed(d.removed).added(d.added) //Delta(removed ++ d.removed, added ++ d.added)

  def nonEmpty = removed.nonEmpty || added.nonEmpty
}

object Delta {
  val empty = Delta(Seq(), Seq())
  def apply(): Delta = empty

}

/**
 * Facilities to write easy compilers easily
 */
abstract class GlobalCompiler(
  override val constraintMatcher: PartialFunction[CSPOMConstraint[_], CSPOMConstraint[_]])
  extends ConstraintCompiler {
  type A = CSPOMConstraint[_]

  def compile(c: CSPOMConstraint[_], problem: CSPOM, data: A) = {
    replaceCtr(c, data, problem)
  }
}

object Ctr {
  def unapply(c: CSPOMConstraint[_]): Option[(Symbol, Seq[CSPOMExpression[_]], Map[String, Any])] = {
    if (c.nonReified) {
      Some((c.function, c.arguments, c.params))
    } else {
      None
    }
  }
}

object CSeq {

}