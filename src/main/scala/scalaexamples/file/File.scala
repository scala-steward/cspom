package scalaexamples.file;

import cspom.{ CSPParseException, CSPOM }
import java.io.FileWriter

final object File {
  val FILENAME = "crossword-m1-debug-05-01.xml";

  def main(args: Array[String]) {
    val cspom = try CSPOM.load(File.getClass.getResource(FILENAME)) catch {
      case e: CSPParseException =>
        println(e.getMessage() + " at line " + e.lineNumber);
        throw e;
      case e => throw e;
    }
    println(cspom);

    val writer = new FileWriter(FILENAME + ".gml");
    try {
      writer.append(cspom.toGML);
    } finally {
      writer.close();
    }

  }
}