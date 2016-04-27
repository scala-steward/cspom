package cspom.compiler
import cspom.CSPOM
import cspom.CSPOMConstraint
import cspom.variable.CSPOMConstant
import cspom.extension.Relation
import cspom.variable.CSPOMConstant
import cspom.variable.CSPOMVariable
import cspom.variable.SimpleExpression
import com.typesafe.scalalogging.LazyLogging
import scala.collection.mutable.HashMap
import cspom.extension.MDD
import scala.collection.mutable.WeakHashMap
import cspom.variable.IntVariable
import cspom.extension.IdEq
import cspom.variable.BoolVariable
import cspom.variable.CSPOMExpression
import cspom.variable.IntExpression
import cspom.variable.BoolExpression
import cspom.util.RangeSet
import cspom.util.Infinitable
import cspom.util.Finite
import cspom.variable.FreeVariable
import cspom.util.IntInterval

/**
 * Detects and removes constants from extensional constraints
 */
class ReduceRelations extends ConstraintCompilerNoData with LazyLogging {

  private val cache = new HashMap[(IdEq[Relation[_]], IndexedSeq[RangeSet[Infinitable]]), (Seq[Int], Relation[Int])]

  override def matchBool(c: CSPOMConstraint[_], problem: CSPOM) = {
    //println(c)
    c.function == 'extension && c.nonReified
  }

  def compile(c: CSPOMConstraint[_], problem: CSPOM) = {

    val Some(relation: Relation[Int] @unchecked) = c.params.get("relation")
    val cargs = c.arguments.toIndexedSeq
    val args: IndexedSeq[RangeSet[Infinitable]] = cargs.map {
      case IntExpression(e)  => IntExpression.implicits.ranges(e)
      case BoolExpression(e) => RangeSet(BoolExpression.span(e))
      case _: FreeVariable   => RangeSet.allInt
      case _                 => throw new IllegalArgumentException()
    }

    logger.info(s"will reduce $relation for $args")

    val (vars, cached) = cache.getOrElseUpdate((IdEq(relation), args), {
      logger.info("reducing !")
      val vars = c.arguments.zipWithIndex.collect {
        case (c: CSPOMVariable[_], i) => i
      }

      val filtered = relation.filter((k, i) => args(k).intersects(IntInterval.singleton(i)))

      logger.info(s"filtered: ${filtered ne relation}")

      val projected = if (vars.size < c.arguments.size) {
        filtered.project(vars)
      } else {
        filtered
      }

      logger.info(s"projected: ${projected ne filtered}")

      val reduced = projected match {
        case p: MDD[_] => p.reduce
        case p         => p
      }

      logger.info(s"reduced: ${reduced ne projected}")

      (vars, reduced)
    })

    if (relation ne cached) {

      logger.info(s"$relation -> $cached")
      replaceCtr(c,
        CSPOMConstraint('extension)(vars.map(cargs): _*) withParams (c.withParam("relation" -> cached).params),
        problem)

    } else {
      Delta.empty
    }

  }

  def selfPropagation = false

}
