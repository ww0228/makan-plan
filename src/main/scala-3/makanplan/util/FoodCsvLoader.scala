package makanplan.util

import scala.io.Source

object FoodCsvLoader {

  // one CSV row addressed by header names (case-insensitive lookup)
  final case class CsvRow(values: Map[String, String]) {
    def get(key: String): Option[String] =
      values.get(key)
        .orElse(values.get(key.toLowerCase))
        .orElse(values.get(key.toUpperCase))
  }

  // load CSV from classpath (e.g. "/csv/Recipes.csv") and map rows to CsvRow
  def parseRowsFromResource(resource: String, sep: Char = ','): List[CsvRow] = {
    val is = Option(getClass.getResourceAsStream(resource))
      .getOrElse(throw new IllegalArgumentException(s"Resource not found on classpath: $resource"))
    val src = Source.fromInputStream(is, "UTF-8")
    try {
      val text = src.mkString                      // read whole file
      val rows = parseCsv(text, sep)               // raw matrix
      if (rows.isEmpty) Nil
      else {
        val header = rows.head                     // first row = header
        rows.tail.map { r =>
          val m = header.zipAll(r, "", "").toMap   // align cols safely
          CsvRow(m)
        }
      }
    } finally src.close()                          // always close
  }

  // RFC4180-ish CSV parser: quotes, escaped quotes, CR/LF, newlines inside quotes
  def parseCsv(text: String, sep: Char = ','): List[List[String]] = {
    val out  = collection.mutable.ListBuffer[List[String]]()
    val cur  = collection.mutable.ListBuffer[String]()
    val buf  = new StringBuilder
    var i    = 0
    val n    = text.length
    var inQ  = false                                // are we inside quotes?

    def endField(): Unit = { cur += buf.result(); buf.clear() } // push field
    def endRow(): Unit   = { endField(); out += cur.toList; cur.clear() } // push row

    while (i < n) {
      val c = text.charAt(i)
      if (inQ) {
        if (c == '"') {
          val dbl = i + 1 < n && text.charAt(i + 1) == '"'
          if (dbl) { buf += '"'; i += 1 } else inQ = false     // "" -> "
        } else buf += c
      } else c match {
        case '"'             => inQ = true
        case ch if ch == sep => endField()
        case '\r'            => endRow(); if (i + 1 < n && text.charAt(i + 1) == '\n') i += 1 // CRLF
        case '\n'            => endRow()
        case other           => buf += other
      }
      i += 1
    }
    if (inQ) ()                                  // tolerate EOF within quotes
    if (buf.nonEmpty || cur.nonEmpty) endRow()   // flush last row
    out.toList
  }

  // slug used as a default image name when CSV image is missing
  def jpg(s: String): String =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
      .replaceAll("\\p{InCombiningDiacriticalMarks}+", "") // strip accents
      .toLowerCase
      .replaceAll("[^a-z0-9]+", "-")                       // non-alnum -> dash
      .replaceAll("(^-|-$)", "")                           // trim dashes

  // alias kept for older callers
  def slugify(s: String): String = jpg(s)
}
