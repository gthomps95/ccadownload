/**
  * Created by gthompson on 10/19/17.
  */
object StringUtils {
  implicit class StringExt(val value: Option[String]) {
    def getForCsv(includeComma: Boolean = true): String = {
      val text = value match {
        case None => ""
        case Some(s) => s.replace(",", " ").replace("\n", " ").replace("\t", " ").split(" ").map(_.trim).filter(!_.isEmpty).mkString(" ")
      }

      text + (if (includeComma) "," else "")
    }
  }
}
