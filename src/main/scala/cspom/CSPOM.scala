package cspom

import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.util.zip.GZIPInputStream

import scala.Iterator
import scala.collection.JavaConversions
import scala.language.implicitConversions
import scala.util.parsing.combinator.JavaTokenParsers
import scala.util.parsing.input.CharSequenceReader

import org.apache.tools.bzip2.CBZip2InputStream

import com.typesafe.scalalogging.LazyLogging

import cspom.dimacs.CNFParser
import cspom.extension.Relation
import cspom.extension.Table
import cspom.extension.Table
import cspom.flatzinc.FlatZincParser
import cspom.variable.BoolVariable
import cspom.variable.CSPOMConstant
import cspom.variable.CSPOMExpression
import cspom.variable.CSPOMSeq
import cspom.variable.CSPOMVariable
import cspom.variable.FreeVariable
import cspom.variable.IntVariable
import cspom.variable.SimpleExpression
import cspom.xcsp.XCSPParser

object NameParser extends JavaTokenParsers {

  def parse: Parser[(String, Seq[Int])] = ident ~ rep("[" ~> wholeNumber <~ "]") ^^ {
    case n ~ i => (n, i.map(_.toInt))
  }
}

/**
 *
 * CSPOM is the central class of the Constraint Satisfaction Problem Object
 * Model. You can create a problem from scratch by instantiating this class and
 * then using the ctr and var methods. The CSPOM.load() method can be used in
 * order to create a CSPOM object from an XCSP file.
 * <p>
 * The CSPOM class adheres to the following definition :
 *
 * A CSP is defined as a pair (X, C), X being a finite set of variables, and C a
 * finite set of constraints.
 *
 * A domain is associated to each variable x in X. Each constraint involves a
 * finite set of variables (its scope) and defines a set of allowed and
 * forbidden instantiations of these variables.
 *
 * @author Julien Vion
 * @see CSPOMConstraint
 * @see CSPOMVariable
 *
 */
class CSPOM extends LazyLogging {

  /**
   * Map used to easily retrieve a variable according to its name.
   */
  private val namedExpressions = collection.mutable.LinkedHashMap[String, CSPOMExpression[_]]()

  private val expressionNames = collection.mutable.HashMap[CSPOMExpression[_], Set[String]]().withDefaultValue(Set.empty)

  private val containers = collection.mutable.HashMap[CSPOMExpression[_], Set[(CSPOMSeq[_], Int)]]().withDefaultValue(Set.empty)

  private val ctrV = collection.mutable.HashMap[CSPOMExpression[_], Set[CSPOMConstraint[_]]]().withDefaultValue(Set.empty)

  private val annotations = collection.mutable.HashMap[String, Annotations]().withDefaultValue(Annotations())

  /**
   * Collection of all constraints of the problem.
   */
  private val _constraints = collection.mutable.LinkedHashSet[CSPOMConstraint[_]]()

  def getExpressions = JavaConversions.asJavaCollection(namedExpressions)

  /**
   * @param variableName
   *            A variable name.
   * @return The variable with the corresponding name.
   */
  def expression(name: String): Option[CSPOMExpression[_]] = {
    NameParser.parse(new CharSequenceReader(name)).map(Some(_)).getOrElse(None).flatMap {
      case (n, s) => getInSeq(namedExpressions.get(n), s)
    }

  }

  def getContainers(e: CSPOMExpression[_]) = containers(e)

  def addAnnotation(expressionName: String, annotationName: String, annotation: Any): Unit = {
    annotations(expressionName) += (annotationName -> annotation)
  }

  def getAnnotations(expressionName: String) = annotations(expressionName)

  private def getInSeq(e: Option[CSPOMExpression[_]], s: Seq[Int]): Option[CSPOMExpression[_]] = {
    if (s.isEmpty) {
      e
    } else {
      e.collect {
        case v: CSPOMSeq[_] => getInSeq(Some(v(s.head)), s.tail)
      }
        .flatten
    }
  }

  def namesOf(e: CSPOMExpression[_]): Iterable[String] = expressionNames(e) ++ containers(e).flatMap {
    case (seq, index) => namesOf(seq).map(s => s"$s[$index]")
  }

  def variable(name: String): Option[CSPOMVariable[_]] = {
    expression(name).collect {
      case v: CSPOMVariable[_] => v
    }
  }

  def constraints = _constraints.iterator

  def constraintSet = _constraints //.toSet

  val getConstraints = JavaConversions.asJavaIterator(constraints)

  def nameExpression[A <: CSPOMExpression[_]](e: A, n: String): A = {
    require(!namedExpressions.contains(n), s"${namedExpressions(n)} is already named $n")
    namedExpressions += n -> e
    expressionNames(e) += n
    registerContainer(e)
    e
  }

  private def registerContainer(e: CSPOMExpression[_]): Unit = {
    e match {
      case s: CSPOMSeq[_] =>
        for ((c, i) <- s.withIndex) {
          containers(c) += ((s, i))
          registerContainer(c)
        }
      case _ =>
    }
  }

  /**
   * Adds a constraint to the problem.
   *
   * @param constraint
   *            The constraint to add.
   */
  private def addConstraint[A](constraint: CSPOMConstraint[A]) = {

    require(!_constraints(constraint),
      "The constraint " + constraint + " already belongs to the problem");

    _constraints += constraint

    for (
      v <- constraint.fullScope
    ) {
      ctrV(v) += constraint
      registerContainer(v)
    }

    constraint
  }

  def removeConstraint(c: CSPOMConstraint[_]) {
    require(_constraints(c), s"$c does not involve $this (not in ${_constraints})")
    _constraints -= c

    //require((Iterator(c.result) ++ c.arguments).forall(ctrV(_)(c)))

    for (
      v <- c.fullScope
    ) {

      ctrV(v) -= c
      if (ctrV(v).isEmpty) {
        ctrV -= v
        //       freeContainer(v)
      }

      if (!isReferenced(v)) {
        removeContainer(v)
      }

    }
  }

  def removeContainer(e: CSPOMExpression[_]): Unit = {
    //assert(!isReferenced(e))
    e match {
      case s: CSPOMSeq[_] =>
        for ((e, i) <- s.withIndex) {
          containers(e) -= ((s, i))
          if (!isReferenced(e)) {
            removeContainer(e)
          }
        }
      case _ =>
    }
  }

  def isReferenced(e: CSPOMExpression[_]): Boolean =
    ctrV(e).nonEmpty || expressionNames(e).nonEmpty || containers(e).nonEmpty 

  def constraints(v: CSPOMExpression[_]): Set[CSPOMConstraint[_]] = {
    ctrV(v) // ++ containers(v).flatMap { case (container, _) => constraints(container) }
  }

  def deepConstraints(v: CSPOMExpression[_]): Set[CSPOMConstraint[_]] = {
    ctrV(v) ++ containers(v).flatMap { case (container, _) => deepConstraints(container) }
  }

  def replaceExpression(which: CSPOMExpression[_], by: CSPOMExpression[_]): Seq[(CSPOMExpression[_], CSPOMExpression[_])] = {
    //logger.warn(s"replacing $which (${namesOf(which)}) with $by (${namesOf(by)})") // from ${Thread.currentThread().getStackTrace.toSeq}")
    require(which != by, s"Replacing $which with $by")
    //require((namesOf(which).toSet & namesOf(by).toSet).isEmpty)
    var replaced = List[(CSPOMExpression[_], CSPOMExpression[_])]()

    for (n <- expressionNames(which)) {
      namedExpressions(n) = by
      expressionNames(by) += n
    }
    expressionNames.remove(which)
    for ((c, i) <- containers(which)) {
      val nc = c.replaceVar(which, by)
      replaced ++:= replaceExpression(c, nc)

      removeContainer(c)
      registerContainer(nc)
    }

    //    containers -= which

    //    lazy val error = namedExpressions.filterNot {
    //      case (n, v) => namesOf(v).exists(_ eq n)
    //    }

    //assert(error.isEmpty, error)

    //    {
    //      case (n, v) =>
    //        val r = namesOf(v).exists(_ eq n)
    //        //if (!r) println(s"$n not in namesOf($v)")
    //        r
    //    })

    (which, by) :: replaced

  }

  def referencedExpressions: Seq[CSPOMExpression[_]] = {
    (ctrV.keysIterator.flatMap(_.flatten) ++ namedExpressions.values).toSeq.distinct
    // ctrV.keySet ++ namedExpressions.values
  }

  def expressionsWithNames: Seq[(String, CSPOMExpression[_])] = {
    namedExpressions.toSeq
  }

  def ctr[A](c: CSPOMConstraint[A]): CSPOMConstraint[A] = {
    if (_constraints(c)) {
      logger.warn(s"$c already belongs to the problem")
      c
    } else {
      addConstraint(c)
    }
  }

  def ctr(v: SimpleExpression[Boolean]) = {
    addConstraint(CSPOMConstraint('eq, Seq(v, CSPOMConstant(true))))
  }

  def is(name: Symbol, scope: Seq[CSPOMExpression[_]], params: Map[String, Any] = Map()): FreeVariable = {
    val result = new FreeVariable()
    ctr(CSPOMConstraint(result, name, scope, params))
    result
  }

  def isInt(name: Symbol, scope: Seq[CSPOMExpression[_]], params: Map[String, Any] = Map()): IntVariable = {
    val result = IntVariable.free()
    ctr(CSPOMConstraint(result, name, scope, params))
    result
  }

  def isBool(name: Symbol, scope: Seq[CSPOMExpression[_]], params: Map[String, Any] = Map()): BoolVariable = {
    val result = new BoolVariable()
    ctr(CSPOMConstraint(result, name, scope, params))
    result
  }

  override def toString = {
    val vn = new VariableNames(this)
    val vars = referencedExpressions.map(e => (vn.names(e), e)).sortBy(_._1).map {
      case (name, variable) => s"$name: $variable"
    }.mkString("\n")

    val cons = constraints.map(_.toString(vn)).mkString("\n")

    s"$vars\n$cons\n${namedExpressions.size} named expressions, ${ctrV.size} first-level expressions and ${constraints.size} constraints"
  }

  /**
   * Generates the constraint network graph in the GML format. N-ary
   * constraints are represented as nodes.
   *
   * @return a String containing the GML representation of the constraint
   *         network.
   */
  def toGML = {
    val stb = new StringBuilder();
    stb.append("graph [\n");
    stb.append("directed 0\n");

    val vn = new VariableNames(this)

    val variables = referencedExpressions
      .flatMap(_.flatten)
      .collect {
        case e: CSPOMVariable[_] => e -> vn.names(e)
      }
      .toMap

    for (k <- variables.values.toSeq.sorted) {
      stb.append(s"""
          node [
            id "$k"
            label "$k"
          ]
          """)

    }

    var gen = 0;

    constraints.flatMap { c =>
      c.fullScope.flatMap(_.flatten).collect {
        case v: CSPOMVariable[_] => variables(v)
      } match {
        case Seq(source, target) => s"""
          edge [
            source "$source"
            target "$target"
            label "${c.function.name}"
          ]
          """
        case s =>
          gen += 1
          s"""
          node [
            id "cons$gen"
            label "${c.function.name}"
            graphics [ fill "#FFAA00" ]
          ]
          """ ++ s.flatMap(v => s"""
          edge [
            source "cons$gen"
            target "$v"
          ]
          """)
      }
    }.addString(stb)

    stb.append("]\n").toString
  }

}

object CSPOM {

  val VERSION = "CSPOM 2.4"

  /**
   * Opens an InputStream according to the given URL. If URL ends with ".gz"
   * or ".bz2", the stream is inflated accordingly.
   *
   * @param url
   *            URL to open
   * @return An InputStream corresponding to the given URL
   * @throws IOException
   *             If the InputStream could not be opened
   */
  @throws(classOf[IOException])
  def problemInputStream(url: URL): InputStream = {

    val path = url.getPath

    val is = url.openStream

    if (path endsWith ".gz") {
      new GZIPInputStream(is)
    } else if (path endsWith ".bz2") {
      is.read()
      is.read()
      new CBZip2InputStream(is)
    } else {
      is
    }

  }

  /**
   * Loads a CSPOM from a given XCSP file.
   *
   * @param xcspFile
   *            Either a filename or an URI. Filenames ending with .gz or .bz2
   *            will be inflated accordingly.
   * @return The loaded CSPOM
   * @throws CSPParseException
   *             If the given file could not be parsed.
   * @throws IOException
   *             If the given file could not be read.
   * @throws DimacsParseException
   */
  @throws(classOf[CSPParseException])
  def load(xcspFile: String): (CSPOM, Map[Symbol, Any]) = {
    val uri = new URI(xcspFile)

    if (uri.isAbsolute) {
      load(uri.toURL)
    } else {
      load(new URL("file://" + uri));
    }

  }

  /**
   * Loads a CSPOM from a given XCSP file.
   *
   * @param url
   *            An URL locating the XCSP file. Filenames ending with .gz or
   *            .bz2 will be inflated accordingly.
   * @return The loaded CSPOM and the list of original variable names
   * @throws CSPParseException
   *             If the given file could not be parsed.
   * @throws IOException
   *             If the given file could not be read.
   * @throws DimacsParseException
   */
  @throws(classOf[CSPParseException])
  @throws(classOf[IOException])
  def load(url: URL): (CSPOM, Map[Symbol, Any]) = {
    val problemIS = problemInputStream(url);

    url.getFile match {
      case name if name.contains(".xml") => XCSPParser.parse(problemIS)
      case name if name.contains(".cnf") => CNFParser.parse(problemIS)
      case name if name.contains(".fzn") => FlatZincParser.parse(problemIS)
      case _                             => throw new IllegalArgumentException("Unhandled file format");
    }

  }

  def apply(f: CSPOM => Any): CSPOM = {
    val p = new CSPOM()
    f(p)
    p
  }

  def ctr(v: SimpleExpression[Boolean])(implicit problem: CSPOM): CSPOMConstraint[Boolean] = problem.ctr(v)

  def ctr[A](c: CSPOMConstraint[A])(implicit problem: CSPOM): CSPOMConstraint[A] = problem.ctr(c)

  implicit class SeqOperations[A](vars: Seq[SimpleExpression[A]]) {
    def in(rel: Seq[Seq[A]]): CSPOMConstraint[Boolean] = in(new Table(rel))
    def notIn(rel: Seq[Seq[A]]): CSPOMConstraint[Boolean] = notIn(new Table(rel))

    def in(rel: Relation[A]): CSPOMConstraint[Boolean] = CSPOMConstraint('extension, vars, Map("init" -> false, "relation" -> rel))
    def notIn(rel: Relation[A]) = CSPOMConstraint('extension, vars, Map("init" -> true, "relation" -> rel))
  }

  implicit def seq2Rel(s: Seq[Seq[Int]]) = new Table(s)

  implicit def constant[A <: AnyVal](c: A): CSPOMConstant[A] = CSPOMConstant(c)

  implicit def seq2CSPOMSeq[A](c: Seq[CSPOMExpression[A]]): CSPOMSeq[A] = CSPOMSeq(c: _*)

  implicit def constantSeq[A <: AnyVal](c: Seq[A]): CSPOMSeq[A] = CSPOMSeq(c.map(constant): _*)

  import language.experimental.macros

  import scala.reflect.macros.blackbox.Context
  import scala.util.Try

  implicit class MatrixContext(sc: StringContext) {
    def matrix(): Array[Array[Int]] = macro matrixImpl
  }

  def matrixImpl(c: Context)(): c.Expr[Array[Array[Int]]] = {
    import c.universe.{ Try => _, _ }

    val matrix = Try {
      c.prefix.tree match {
        case Apply(_, List(Apply(_, List(Literal(Constant(raw: String)))))) =>

          def toArrayAST(c: List[TermTree]) =
            Apply(Select(Select(Ident(TermName("scala")), TermName("Array")), TermName("apply")), c)

          val matrix = raw
            .split("\n")
            .map(_.trim)
            .filter(_.nonEmpty)
            .map {
              _.split(",").map(_.trim.toInt)
            }
          if (matrix.map(_.length).distinct.size != 1) {
            c.abort(c.enclosingPosition, "rows of matrix do not have the same length")
          }

          val matrixAST = matrix
            .map(_.map(i => Literal(Constant(i))))
            .map(i => toArrayAST(i.toList))

          toArrayAST(matrixAST.toList)
      }
    }

    c.Expr(matrix.getOrElse(c.abort(c.enclosingPosition, "not a matrix of Int")))
  }

}


