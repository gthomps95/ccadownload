import java.io.{File, FileWriter, PrintWriter}
import java.time.{LocalDate, Period}
import java.time.format.DateTimeFormatter

import org.openqa.selenium.remote.RemoteWebDriver

import scala.collection.JavaConverters._
import WebDriverUtils._
import StringUtils._

case class BillingRecord(client: Client,
                         id: Option[String] = None,
                         date: Option[LocalDate] = None,
                         url: Option[String] = None,
                         description: Option[String] = None,
                         amount: Option[String] = None
                        )

object BillingRecordBuilder {
  private val idPattern = "([ip][0-9]+)".r.unanchored

  def build(driver: RemoteWebDriver, client: Client): Seq[BillingRecord] = {
    driver.checkGetUrl(s"https://office.mhpoffice.com/office/client/${client.id}/billing")

    val basePath = "//*[@id=\"content\"]/div[@class=\"reb-rows\"]/table/tbody/tr"
    val rows = driver.findElementsByXPath(basePath).asScala
    val paths = (1 until rows.length + 1).map(index => s"$basePath[" + index + "]")

    for (path <- paths) yield buildRecord(driver, client, path)
  }

  //*[@id="content"]/div[3]/table/tbody/tr[1]/td[2]/span[1]

  private def buildRecord(driver: RemoteWebDriver, client: Client, path: String) = {
    val dateStr = driver.getAttributeFromXPath(path + "/td[2]/span[2]", "innerHTML")
    val url = driver.getAttributeFromXPath(path + "/td[3]/a", "href")
    val desc = driver.getTextFromXpath(path + "/td[3]/a")
    val amt = driver.getTextFromXpath(path + "/td[4]")

    val id = url collect { case idPattern(s) => s }

    val date = dateStr match {
      case None => None
      case Some(s) if s.trim.toLowerCase == "today" => Some(LocalDate.now)
      case Some(s) if s.trim.toLowerCase == "yesterday" => Some(LocalDate.now.minus(Period.ofDays(1)))
      case Some(s) => Some(LocalDate.parse(s, DateTimeFormatter.ofPattern("MM/dd/yy")))
    }

    BillingRecord(client = client, id = id, date = date, url = url, description = desc, amount = amt)
  }
}

object BillingRecordOutput {
  def output(dest: File, records: Seq[BillingRecord]): Unit = {
    val pw = new PrintWriter(new FileWriter(dest, true))
    pw.println("clinician,clientid,id,date,description,amount")

    for (record <- records) {
      pw.print(record.client.clinician.getForCsv())
      pw.print(Some(record.client.id).getForCsv())
      pw.print(record.id.getForCsv())
      pw.print(record.date.map(d => DateTimeFormatter.ofPattern("MM/dd/yyyy").format(d)).getForCsv())
      pw.print(record.description.getForCsv())
      pw.print(record.amount.getForCsv(false))
      pw.println()
    }

    pw.flush()
    pw.close()
  }
}

object BillingRecordDownloadPdf {
  def download(driver: RemoteWebDriver, records: Seq[BillingRecord]): Option[Boolean] = {
    val names = records
      .filter(r => r.date.get.getYear >= 2017)
      .map(r => r.id.get)

    if (names.isEmpty)
      return None

    val name = names.mkString(",")

    val url = s"https://office.mhpoffice.com/office/export/bills/$name.pdf"

    val record = records.head
    val clientDir = ClientDir.get(record.client)

    val dir = new File(s"$clientDir")

    if (!dir.exists())
      dir.mkdirs()

    val filename = "billing_invoice.pdf"

    val dl = FileDownload(new File(s"${dir.getAbsolutePath}/$filename"), url, Some(record.client.id))
    Some(FileDownloader.downloadFile(driver, dl))
  }
}