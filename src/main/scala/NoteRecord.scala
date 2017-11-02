import java.io.{File, FileWriter, PrintWriter}
import java.time.{LocalDate, Period}
import java.time.format.DateTimeFormatter

import org.openqa.selenium.remote.RemoteWebDriver

import scala.collection.JavaConverters._
import StringUtils._
import WebDriverUtils._
import org.apache.commons.io.FilenameUtils


case class NoteRecord(client: Client,
                      id: Option[String] = None,
                      date: Option[LocalDate] = None,
                      url: Option[String] = None,
                      documentUrl: Option[String] = None,
                      title: Option[String] = None,
                      form: Option[String] = None,
                      deleted: Boolean,
                      downloadResult: Option[String] = None
                     )

object NoteRecordBuilder {
  private val idPattern = "([D][0-9]+)".r.unanchored
  def build(driver: RemoteWebDriver, client: Client): Seq[NoteRecord] = {
    driver.checkGetUrl(s"https://office.mhpoffice.com/office/client/${client.id}/note")

    val rows = driver.findElementsByXPath("//*[@id=\"content\"]/div[5]/table/tbody/tr").asScala
    val paths = (1 until rows.length + 1).map(index => "//*[@id=\"content\"]/div[5]/table/tbody/tr[" + index + "]")

    val records = (for (path <- paths) yield buildRecord(driver, client, path)).flatten
    for (record <- records) yield applyDocumentUrl(driver, record)
  }

  private def applyDocumentUrl(driver: RemoteWebDriver, record: NoteRecord): NoteRecord = {
    record.url match {
      case None => record
      case Some(s) =>
        driver.get(s)

        val printButtons = driver.findElementsByXPath("//*[@id=\"content\"]/div[2]/div/div/div[1]/a").asScala
        val downloadButtons = driver.findElementsByXPath("//*[@id=\"content\"]/div[2]/div/div/a").asScala
        val buttons = printButtons ++ downloadButtons

        val documentUrl = if (buttons.nonEmpty) Some(buttons.head.getAttribute("href")) else None

        record.copy(documentUrl = documentUrl)
    }
  }

  private def buildRecord(driver: RemoteWebDriver, client: Client, path: String): Option[NoteRecord] = {
    val row = driver.findElementByXPath(path)
    val rowc = row.getAttribute("class")

    val deleted = rowc == "noterow-deleted"

    val dateStr = driver.getTextFromXpath(path + "/td[1]")

    if (dateStr.getOrElse("").startsWith("No Results"))
      return None

    val url = driver.getAttributeFromXPath(path + "/td[2]/a", "href")
    val title = driver.getTextFromXpath(path + "/td[2]/a")
    val form = driver.getTextFromXpath(path + "/td[3]")

    val id = url collect { case idPattern(s) => s }

    val date = dateStr match {
      case None => None
      case Some(s) if s.trim.toLowerCase == "today" => Some(LocalDate.now)
      case Some(s) if s.trim.toLowerCase == "yesterday" => Some(LocalDate.now.minus(Period.ofDays(1)))
      case Some(s) => Some(LocalDate.parse(s, DateTimeFormatter.ofPattern("MMM dd, yyyy")))
    }

    Some(NoteRecord(client = client, id = id, date = date, url = url, title = title, form = form, deleted = deleted))
  }
}

object NoteRecordOutput {
  def output(dest: File, records: Seq[NoteRecord]): Unit = {
    val pw = new PrintWriter(new FileWriter(dest, true))
    pw.println("id,date,title,form,deleted,result")

    for (record <- records) {
      pw.print(record.id.getForCsv())
      pw.print(record.date.map(d => DateTimeFormatter.ofPattern("MM/dd/yyyy").format(d)).getForCsv())
      pw.print(record.title.getForCsv())
      pw.print(record.form.getForCsv())
      pw.print(Some(s"${record.deleted}").getForCsv())
      pw.print(record.downloadResult.getForCsv(false))
      pw.println()
    }

    pw.flush()
    pw.close()
  }
}


object NoteRecordDownloader {
  private val extPattern = "[0-9]([A-Za-z]+)\\?".r

  def download(driver: RemoteWebDriver, records: Seq[NoteRecord]): Seq[NoteRecord] = {
    records.filter(!_.deleted).map(dowloadRecord(driver, _))
  }

  def dowloadRecord(driver: RemoteWebDriver, record: NoteRecord): NoteRecord = {
    val clientDir = ClientDir.get(record.client)

    record.documentUrl match {
      case None => record
      case Some(url) =>

        var filename = FilenameUtils.getName(url).split("\\?").head

        if (!filename.contains(".")) {
          filename = filename match {
            case s if s.toLowerCase.endsWith("pdf") => s"$s.pdf"
            case s if s.toLowerCase.endsWith("png") => s"$s.png"
            case s if s.toLowerCase.endsWith("docx") => s"$s.docx"
            case s if s.toLowerCase.endsWith("doc") => s"$s.doc"
            case s if s.toLowerCase.endsWith("xlsx") => s"$s.xlsx"
            case s if s.toLowerCase.endsWith("xls") => s"$s.xls"
            case s if s.toLowerCase.endsWith("jpeg") => s"$s.jpeg"
            case s if s.toLowerCase.endsWith("jpg") => s"$s.jpg"
            case s => s
          }
        }

        if (!filename.contains(".") && extPattern.findAllIn(url).matchData.nonEmpty) {
          val ext = extPattern.findAllIn(url).group(1)
          filename = s"$filename.$ext"
        }

        filename = record.date match {
          case None => filename
          case Some(d) => s"${DateTimeFormatter.ofPattern("yyyy-MM-dd").format(d)} - $filename"
        }

        val notesDir = new File(s"$clientDir/notes")

        if (!notesDir.exists())
          notesDir.mkdirs()

        val dl = FileDownload(new File(s"${notesDir.getAbsolutePath}/$filename"), url, Some(record.client.id))
        val result = if (FileDownloader.downloadFile(driver, dl)) "Success" else "Failure"
        record.copy(downloadResult = Some(result))
    }
  }
}