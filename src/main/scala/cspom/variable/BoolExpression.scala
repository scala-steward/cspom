package cspom.variable

import cspom.util.{Infinitable, IntInterval, Interval, RangeSet}

object BoolExpression extends SimpleExpression.Typed[Boolean] {
  def coerce(e: CSPOMExpression[_]): SimpleExpression[Boolean] = e match {
    case _: FreeVariable => new BoolVariable()
    case BoolExpression(e) => e
    case IntExpression(e) => e
      .intersected(IntExpression(RangeSet(IntInterval(0, 1))))
      .asInstanceOf[SimpleExpression[Boolean]]

    case e => throw new IllegalArgumentException(s"Cannot convert $e to boolean expression")
  }

  def is01(e: CSPOMExpression[_]): Boolean = e match {
    case BoolExpression(e) => true
    case IntExpression(e) => IntExpression.is01(e)
    case _ => false
  }

  def span(b: SimpleExpression[Boolean]): Interval[Infinitable] = b match {
    case _: BoolVariable => IntInterval(0, 1)
    case CSPOMConstant(true) => IntInterval.singleton(1)
    case CSPOMConstant(false) => IntInterval.singleton(0)
    case _ => throw new IllegalStateException
  }

  object seq {
    def unapply(c: CSPOMExpression[_]): Option[CSPOMSeq[Boolean]] =
      c match {
        case s: CSPOMSeq[_] =>
          CSPOMSeq.collectAll(s) {
            case BoolExpression(e) => e
          }
            .map(CSPOMSeq(_, s.definedIndices))
        case _ => None
      }
  }

  object simpleSeq {
    def unapply(c: CSPOMExpression[_]): Option[Seq[SimpleExpression[Boolean]]] =
      seq.unapply(c).flatMap(SimpleExpression.simpleCSPOMSeq.unapply)
  }

  object bool01 {
    def unapply(c: CSPOMExpression[_]): Option[SimpleExpression[_]] = {
      c match {
        case c: SimpleExpression[_] if is01(c) => Some(c)
        case _ => None
      }
    }
  }

}