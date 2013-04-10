package cspom.extension

import scala.collection.Seq

class Table(val table: List[Array[Int]]) extends Relation {
  // Members declared in scala.collection.IterableLike
  def iterator: Iterator[Array[Int]] = table.iterator
  // Members declared in cspom.extension.Relation
  def arity: Int = table.head.length
  def contains(t: Seq[Int]): Boolean = table.exists(_ sameElements t)
}