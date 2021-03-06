package cspom.util

import scala.collection.SortedSet
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import Intervals.smallIntervals
import Intervals.validIntervals
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class RangeSetTest extends FlatSpec with Matchers with ScalaCheckPropertyChecks {

  def asSet(r: RangeSet[Infinitable]): SortedSet[BigInt] =
    new ContiguousIntRangeSet(r)

  "RangeSet" should "be conform" in {

    val rangeSet1 = RangeSet.empty[Infinitable]
    val rs2 = rangeSet1 ++ IntInterval(1, 10); // {[1, 10]}
    val rs3 = rs2 ++ IntInterval(12, 15); // disconnected range: {[1, 10], [12, 15]} 
    val rs4 = rs3 ++ IntInterval(16, 20); // connected range; {[1, 10], [12, 20]}
    val rs5 = rs4 ++ IntInterval(0, -1); // empty range; {[1, 10], [12, 20]}
    val rs6 = rs5 -- IntInterval(6, 9); // splits [1, 10]; {[1, 5], [10, 10], [12, 20]}

    rangeSet1.toString shouldBe "∅"
    rs2.toString shouldBe "[1‥10]"
    rs3.toString shouldBe "[1‥10]∪[12‥15]"
    rs4.toString shouldBe "[1‥10]∪[12‥20]"
    rs5.toString shouldBe "[1‥10]∪[12‥20]"
    rs6.toString shouldBe "[1‥5]∪{10}∪[12‥20]"
  }

  it should "contain all values when open" in {
    val i: IntInterval = IntInterval.all

    forAll { j: Int => assert(i.contains(j)) }

    val l = IntInterval.atLeast(0)

    forAll { j: Int => l.contains(j) shouldBe j >= 0 }

  }

  it should "merge intersecting Ranges" in {
    val itv1 = IntInterval(0, 10)
    val itv2 = IntInterval(10, 15)

    assert(RangeSet(Seq(itv1, itv2)).isConvex)

  }

  it should "work under disjoint RangeSet" in {

    RangeSet(Seq(
      IntInterval(0, 5),
      IntInterval(-10, -9),
      IntInterval(8, 10),
      IntInterval(-7, -4))) shouldBe RangeSet(Seq(
      IntInterval(-10, -9),
      IntInterval(-7, -4),
      IntInterval(0, 5),
      IntInterval(8, 10)))

  }

  it should "handle empty RangeSet" in {
    val itv1 = RangeSet.empty[Infinitable]
    itv1 shouldBe empty

    itv1 ++ IntInterval(5, 10) shouldBe RangeSet(IntInterval(5, 10))
  }

  it should "handle large values" in {

    asSet(RangeSet.empty[Infinitable] ++ List(0, 2, 1).map(IntInterval.singleton)) should contain theSameElementsInOrderAs
      Seq(0, 1, 2)

    asSet(RangeSet.empty[Infinitable] ++ IntInterval.singleton(Int.MinValue) ++ IntInterval.singleton(Int.MaxValue - 1) ++ IntInterval.singleton(0)) should contain theSameElementsInOrderAs
      Seq(Int.MinValue, 0, Int.MaxValue - 1)

    val s = Set(0, -2147483648, 2, -2).map(IntInterval.singleton)

    (RangeSet.empty ++ s).contents should contain theSameElementsAs s

  }

  it should "merge joint ranges" in {
    val itv1 = RangeSet(IntInterval(0, 6))

    (itv1 ++ IntInterval(-10, -8) ++ IntInterval(8, 10) ++ IntInterval(-8, -4)).contents should contain theSameElementsAs Seq(
      IntInterval(-10, -4), IntInterval(0, 6), IntInterval(8, 10))

    itv1 ++ IntInterval(6, 10) shouldBe RangeSet(IntInterval(0, 10))

    itv1 ++ IntInterval(-5, 0) shouldBe RangeSet(IntInterval(-5, 6))
  }

  it should "be iterable" in {
    forAll(smallIntervals) { i1 =>
      asSet(RangeSet(i1)) should contain theSameElementsInOrderAs i1
    }
  }

  it should "compute difference" in {

    val i = RangeSet(Seq(IntInterval.singleton(2147483647), IntInterval.singleton(0)))
    (i -- IntInterval.singleton(2147483647)) shouldBe RangeSet(IntInterval.singleton(0))
    (RangeSet(IntInterval.singleton(2147483647)) -- IntInterval(1, 688162636)) shouldBe RangeSet(IntInterval.singleton(2147483647))
    RangeSet(IntInterval(0, 5)) -- IntInterval(10, 15) shouldBe RangeSet(IntInterval(0, 5))
    RangeSet(IntInterval(10, 15)) -- IntInterval(0, 5) shouldBe RangeSet(IntInterval(10, 15))
    (RangeSet(IntInterval(0, 10)) -- IntInterval(3, 7)) shouldBe
      RangeSet(Seq(IntInterval(0, 2), IntInterval(8, 10)))
    (RangeSet(IntInterval(0, 10)) -- IntInterval(-10, 5)) shouldBe
      RangeSet(IntInterval(6, 10))
    (RangeSet(IntInterval(0, 10)) -- IntInterval(5, 10)) shouldBe
      RangeSet(IntInterval(0, 4))
    RangeSet(IntInterval(0, 5)) -- IntInterval(-5, 15) shouldBe RangeSet.empty
    RangeSet(IntInterval(0, 5)) -- IntInterval(0, 5) shouldBe RangeSet.empty

    forAll(smallIntervals, validIntervals) { (i1, i2) =>
      //println(s"$i1 -- $i2")
      asSet(RangeSet(i1) -- i2) should contain theSameElementsInOrderAs
        i1.filterNot(i2.contains(_))
    }

    forAll { (s1: Seq[Int], s2: Seq[Int]) =>
      val i1 = RangeSet(s1.map(IntInterval.singleton))
      val i2 = RangeSet(s2.map(IntInterval.singleton))

      asSet(i1 -- i2) should contain theSameElementsAs (s1.toSet -- s2.toSet)
    }

  }

  it should "compute differences of open intervals" in {
    RangeSet(Seq(IntInterval(0, 5), IntInterval(10, 15))) -- IntInterval.atMost(7) shouldBe
      RangeSet(IntInterval(10, 15))

    RangeSet(Seq(IntInterval(0, 5), IntInterval(10, 15))) -- IntInterval.atMost(-2) shouldBe
      RangeSet(Seq(IntInterval(0, 5), IntInterval(10, 15)))

    RangeSet(Seq(IntInterval(0, 5), IntInterval(10, 15))) -- IntInterval.atMost(15) shouldBe
      RangeSet.empty

    forAll { (s1: Seq[Int], b: Int) =>
      val s = RangeSet(s1.map(IntInterval.singleton))
      val d = s -- IntInterval.atMost(b)

      asSet(d) should contain theSameElementsAs s1.distinct.filter(_ > b)
    }
  }

  it should "intersect" in {

    asSet(RangeSet(List(1, 2, 3).map(IntInterval.singleton)) &
      RangeSet(List(2, 3, 4).map(IntInterval.singleton))) should contain theSameElementsAs
      Set(2, 3)

    forAll { (s1: Seq[Int], s2: Seq[Int]) =>
      val i1 = RangeSet(s1.map(IntInterval.singleton))
      val i2 = RangeSet(s2.map(IntInterval.singleton))

      asSet(i1 & i2) should contain theSameElementsAs (s1.toSet & s2.toSet)

    }

  }

  it should "allow infinite ranges" in {
    val all = RangeSet.allInt

    all.toString shouldBe "(-∞‥+∞)"

  }

  it should "detect equality" in {
    RangeSet(IntInterval.all) should not be RangeSet(IntInterval.atLeast(0))
  }

  it should "contain given values" in {
    val r = RangeSet(Seq(IntInterval.singleton(0), IntInterval.singleton(-1)))
    val s = new ContiguousIntRangeSet(r)
    assert(s.contains(0))
    assert(s.contains(-1))

    s shouldBe Set(0, -1)
    s.iterator.toSeq shouldBe Seq(-1, 0)

  }

  it should "detect containment" in {
    forAll { s: Set[Int] =>
      val r = RangeSet(s.map(e => IntInterval.singleton(e)))

      forAll { i: Int =>
        s.contains(i) shouldBe r.intersects(IntInterval.singleton(i))
      }

    }
  }
} 