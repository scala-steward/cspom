package cspom.variable;

import scala.collection.SortedSet

import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

final class ExtensiveDomainTest extends FlatSpec with Matchers with PropertyChecks {

  "Extensive domains" should "behave like sets" in {
    forAll { d: Seq[Int] =>

      whenever(d.nonEmpty) {

        val s = d.toSet

        val intDomain = IntDomain(d)

        s shouldBe intDomain
        intDomain shouldBe s

      }

    }
  }

}
