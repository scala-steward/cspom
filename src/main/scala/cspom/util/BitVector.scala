package cspom.util

import BitVector._
import java.util.Arrays

final object BitVector {
  private val ADDRESS_BITS_PER_WORD = 6
  val WORD_SIZE: Int = 1 << ADDRESS_BITS_PER_WORD
  val MASK: Long = 0xFFFFFFFFFFFFFFFFL

  val empty: BitVector = EmptyBitVector

  def filled(size: Int): BitVector = empty.set(0, size)

  def word(bit: Int): Int = bit >> ADDRESS_BITS_PER_WORD

  def apply(v: Traversable[Int]): BitVector = {
    empty ++ v
  }
}

trait BitVector extends Any {

  def iterator: Iterator[Int] = new Iterator[Int] {
    var current = nextSetBit(0)
    def hasNext = current >= 0
    def next() = {
      val c = current
      current = nextSetBit(current + 1)
      c
    }
  }

  override def toString(): String = iterator.mkString("{", ", ", "}")

  def set(position: Int, status: Boolean): BitVector = {
    if (status) {
      this + position
    } else {
      this - position
    }
  }

  def set(from: Int, until: Int): BitVector = {
    if (from >= until) {
      this
    } else {
      val startWordIndex = word(from)
      val maskFrom = MASK << from
      val lastWordIndex = word(until - 1)
      val maskTo = MASK >>> -until

      val newWords = getWords.padTo(lastWordIndex + 1, 0L)
      val sw = newWords(startWordIndex)

      var changed = false
      if (startWordIndex == lastWordIndex) {
        newWords(startWordIndex) |= (maskFrom & maskTo)
      } else {
        newWords(startWordIndex) |= maskFrom

        val lw = newWords(lastWordIndex)
        newWords(lastWordIndex) |= maskTo

        changed |= (lw != newWords(lastWordIndex))

        for (i <- startWordIndex + 1 until lastWordIndex) {
          if (newWords(i) != MASK) {
            newWords(i) = MASK
            changed = true
          }
        }

      }
      changed |= (sw != newWords(startWordIndex))

      if (changed) {
        if (newWords.length == 1) {
          new SmallBitVector(newWords.head)
        } else {
          LargeBitVector.noShrink(newWords)
        }
      } else {
        this
      }
    }
  }

  def getWords: Array[Long]

  def -(position: Int): BitVector

  def +(position: Int): BitVector = {
    val wordPos = word(position)
    val oldWord = getWord(wordPos)
    val newWord = oldWord | (1L << position)

    if (oldWord == newWord) {
      this
    } else {
      setWordExpand(wordPos, newWord)
    }
  }

  def ++(p: Traversable[Int]): BitVector = {
    var words = getWords
    var change = false

    for (i <- p) {
      val wordPos = word(i)
      if (wordPos >= words.length) {
        words = Arrays.copyOf(words, wordPos + 1)
      }
      val oldWord = words(wordPos)
      val newWord = oldWord | (1L << i)
      if (oldWord != newWord) {
        change = true
        words(wordPos) = newWord
      }
    }

    if (change) {
      words.size match {
        case 0 => EmptyBitVector
        case 1 => new SmallBitVector(words(0))
        case _ => LargeBitVector.noShrink(words)
      }
    } else {
      this
    }
  }

  def --(p: Traversable[Int]): BitVector = p.foldLeft(this)(_ - _)

  def apply(position: Int): Boolean

  def nextSetBit(start: Int): Int

  def prevSetBit(start: Int): Int

  def lastSetBit: Int

  def clearFrom(from: Int): BitVector

  def clearUntil(until: Int): BitVector

  def intersects(bV: BitVector, position: Int): Boolean

  def intersects(bV: BitVector): Int;

  def nbWords: Int

  def ^(bv: BitVector): BitVector

  def &(bv: BitVector): BitVector

  def |(bv: BitVector): BitVector

  def isEmpty: Boolean

  def cardinality: Int

  def getWord(i: Int): Long

  def subsetOf(bv: BitVector): Boolean

  def setWordExpand(pos: Int, word: Long): BitVector

  def setWordShrink(pos: Int, word: Long): BitVector

  def filter(f: Int => Boolean): BitVector
  def filterBounds(f: Int => Boolean): BitVector

}

object EmptyBitVector extends BitVector {
  def &(bv: BitVector): BitVector = this
  def |(bv: BitVector): BitVector = bv
  def -(position: Int): BitVector = this
  def ^(bv: BitVector): BitVector = bv
  def apply(position: Int): Boolean = false
  def cardinality: Int = 0
  def clearFrom(from: Int): BitVector = this
  def clearUntil(to: Int): BitVector = this
  def filter(f: Int => Boolean): BitVector = this
  def filterBounds(f: Int => Boolean): BitVector = this
  def getWord(i: Int): Long = 0L
  def getWords: Array[Long] = Array()
  def intersects(bV: BitVector): Int = -1
  def intersects(bV: BitVector, position: Int): Boolean = false
  def isEmpty: Boolean = true
  def lastSetBit: Int = -1
  def nbWords: Int = 0
  def nextSetBit(start: Int): Int = -1
  def prevSetBit(start: Int): Int = -1
  def setWordExpand(pos: Int, word: Long): BitVector = {
    if (pos == 0) {
      new SmallBitVector(word)
    } else {
      val array = new Array[Long](pos + 1)
      array(pos) = word
      LargeBitVector.noShrink(array)
    }
  }
  def setWordShrink(pos: Int, word: Long): BitVector = ???
  def subsetOf(bv: BitVector): Boolean = true
}
