import java.io.{File, FileWriter, PrintWriter}
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import org.openqa.selenium.remote.RemoteWebDriver

import scala.collection.JavaConverters._
import StringUtils._
import WebDriverUtils._

case class AppointmentRecord(client: Client,
                             id: Option[String] = None,
                             date: Option[LocalDate] = None,
                             time: Option[String] = None,
                             status: Option[String] = None,
                             apptType: String,
                             url: Option[String] = None,
                             comments: Option[String] = None
                            )

object AppointmentRecordBuilder {
  private val idPattern = "(EVT[0-9]+)".r.unanchored

  def build(driver: RemoteWebDriver, client: Client, downloadComments: Boolean): Seq[AppointmentRecord] = {
    driver.checkGetUrl(s"https://office.mhpoffice.com/office/client/${client.id}/schedule#showFuture")

    val upcomingRows = driver.findElementsByXPath("//*[@id=\"content\"]/div[5]/div[2]/div").asScala
    val upcomingPaths = (1 until upcomingRows.length + 1).map(index => PathAndType("Upcoming", "//*[@id=\"content\"]/div[5]/div[3]/div[" + index + "]"))

    val pastDiv = if (upcomingRows.isEmpty) 3 else 5

    val pastRows = driver.findElementsByXPath("//*[@id=\"content\"]/div[" + pastDiv + "]/div[3]/div").asScala
    val pastPaths = (1 until pastRows.length + 1).map(index => PathAndType("Past", "//*[@id=\"content\"]/div[" + pastDiv + "]/div[3]/div[" + index + "]"))

    val paths = upcomingPaths ++ pastPaths
    val records = for (path <- paths) yield buildRecord(driver, client, path)

    if (downloadComments)
      for (record <- records) yield appendComments(driver, record)
    else
      records
  }

  private def buildRecord(driver: RemoteWebDriver, client: Client, path: PathAndType): AppointmentRecord = {
    val dateStr = driver.getAttributeFromXPath(path.path + "/strong", "innerHTML")
    val time = driver.getAttributeFromXPath(path.path + "/small", "innerHTML")
    val status = driver.getAttributeFromXPath(path.path + "/span[1]/span", "innerHTML")
    val url = driver.getAttributeFromXPath(path.path + "/div[1]/a[2]", "href")

    val id = url collect { case idPattern(s) => s }

    val date = dateStr match {
      case None => None
      case Some(s) if s.trim.toLowerCase == "today" => Some(LocalDate.now)
      case Some(s) => Some(LocalDate.parse(s.replace("  ", " "), DateTimeFormatter.ofPattern("MMM d, yyyy")))
    }

    AppointmentRecord(client = client, apptType = path.atype, id = id, date = date, url = url, time = time, status = status)
  }

  private def appendComments(driver: RemoteWebDriver, record: AppointmentRecord): AppointmentRecord = {
    record.url match {
      case None => record
      case Some(s) =>
        driver.get(s)
        val commentFields = driver.findElementsByXPath("//*[@id=\"comment-text\"]").asScala
        if (commentFields.nonEmpty) {
          val comments = commentFields.head.getText
          record.copy(comments = Some(comments))
        } else {
          record
        }
    }
  }
}

case class PathAndType(atype: String, path: String)

object AppointmentRecordOutput {
  def output(dest: File, records: Seq[AppointmentRecord]): Unit = {
    val pw = new PrintWriter(new FileWriter(dest, true))
    pw.println("id,date,time,status,apptType,comments")

    for (record <- records) {
      pw.print(record.id.getForCsv())
      pw.print(record.date.map(d => DateTimeFormatter.ofPattern("MM/dd/yyyy").format(d)).getForCsv())
      pw.print(record.time.getForCsv())
      pw.print(record.status.getForCsv())
      pw.print(Some(record.apptType).getForCsv())
      pw.print(record.comments.getForCsv(false))
      pw.println()
    }

    pw.flush()
    pw.close()
  }
}

