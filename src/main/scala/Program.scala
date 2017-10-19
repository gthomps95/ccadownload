import java.io.{File, FileWriter, PrintWriter}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

import com.typesafe.scalalogging.LazyLogging
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.remote.{DesiredCapabilities, RemoteWebDriver}

import scala.io.Source
import WebDriverUtils._
import StringUtils._
import com.google.common.base.Stopwatch
import play.api.libs.json._

import scala.collection.mutable.ListBuffer

object Program extends App with LazyLogging {
  val downloadAll = true
  val printStats = false
  val allClients = false
  val clientCount = 10

  implicit val clientFormat = Json.format[Client]
  implicit val generalFormat = Json.writes[ClientGeneral]
  implicit val contactFormat = Json.writes[ClientContact]
  implicit val providerFormat = Json.writes[ClientProvider]
  implicit val billFormat = Json.writes[BillingRecord]
  implicit val noteFormat = Json.writes[NoteRecord]
  implicit val apptFormat = Json.writes[AppointmentRecord]

  implicit val historyFormat = Json.writes[History]
  implicit val diagnosisCodeFormat = Json.writes[DiagnosisCode]
  implicit val diagnosisFormat = Json.writes[Diagnosis]
  implicit val allDiagnosisFormat = Json.writes[AllDiagnosis]
  implicit val treatmentPlanFormat = Json.writes[TreatmentPlan]

  implicit val summaryFormat = Json.writes[ClientSummary]

  val now = LocalDateTime.now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
  val basedir = s"/Volumes/USB DISK/CCA/download_$now"
  val controlFile = new File(s"$basedir/control.csv")
  ClientDir.setBasedir(basedir)

  var clients = buildClientList(new File("/Volumes/USB DISK/CCA_Data.json"))
  clients = ClientScrubber.fixClinicianNames(clients)
  clients = ClientScrubber.fixClientNames(clients)

  if (printStats) printStats

  clients = getClients(clients)
  logger.info(s"${clients.length}")

  if (args.length < 2)
    throw new Exception("username and password not supplied.")

  val service = new ChromeDriverService.Builder()
    .usingDriverExecutable(new File("/Users/gthompson/bin/chromedriver"))
    .usingAnyFreePort()
    .build()

  service.start()

  val driver = new RemoteWebDriver(service.getUrl, DesiredCapabilities.chrome())

  val summaries = scala.collection.mutable.ListBuffer[ClientSummary]()

  try {
    login(args(0), args(1))

    var sw = Stopwatch.createStarted()
    var count = 0

    //for (client <- clients.filter(c => c.id == "PPT21885")) {
    for (client <- scala.util.Random.shuffle(clients).take(clientCount)) {
      val clientDir = ClientDir.get(client)

      try {
        count += 1
        logger.info(s"${client.id} - $count")

        sw = Stopwatch.createStarted()

        val isActiveBefore = ClientActivate.isActive(driver, client)
        if (!isActiveBefore)
          ClientActivate.activate(driver, client)

        val general = ClientGeneralBuilder.build(driver, client)
        new PrintWriter(s"$clientDir/general.json") {write(Json.prettyPrint(Json.toJson(general))); close()}

        val contact = ClientContactBuilder.build(driver, client)
        new PrintWriter(s"$clientDir/contact.json") {write(Json.prettyPrint(Json.toJson(contact))); close()}

        val provider = ClientProviderBuilder.build(driver, client)
        new PrintWriter(s"$clientDir/provider.json") {write(Json.prettyPrint(Json.toJson(provider))); close()}

        val billRecords = if (downloadAll) BillingRecordBuilder.build(driver, client) else List[BillingRecord]()
        BillingRecordDownloadPdf.download(driver, billRecords)
        new PrintWriter(s"$clientDir/billing.json") {write(Json.prettyPrint(Json.toJson(billRecords))); close()}
        BillingRecordOutput.output(new File(s"$clientDir/billing.csv"), billRecords)

        var noteRecords = if (downloadAll) NoteRecordBuilder.build(driver, client) else Seq[NoteRecord]()
        noteRecords = NoteRecordDownloader.download(driver, noteRecords)
        new PrintWriter(s"$clientDir/notes.json") {write(Json.prettyPrint(Json.toJson(noteRecords))); close()}
        NoteRecordOutput.output(new File(s"$clientDir/notes.csv"), noteRecords)

        val apptRecords = if (downloadAll) AppointmentRecordBuilder.build(driver, client) else Seq[AppointmentRecord]()
        new PrintWriter(s"$clientDir/appt.json") {write(Json.prettyPrint(Json.toJson(apptRecords))); close()}
        AppointmentRecordOutput.output(new File(s"$clientDir/appointments.csv"), apptRecords)

        val treatmentPlan = TreatmentPlan.downloadTreatmenPlan(driver, client)

        if (!isActiveBefore)
          ClientActivate.deactivate(driver, client)
        val isActiveAfter = ClientActivate.isActive(driver, client)

        sw.stop()

        summaries.append(ClientSummary(client, isActiveBefore, isActiveAfter, general, provider, contact, treatmentPlan))
        writeControlSuccess(count, client, isActiveBefore, isActiveAfter, sw, general, contact, provider, billRecords, noteRecords, apptRecords)
      }
      catch {
        case e: Exception =>
          sw.stop()
          logger.error(s"Error in client ${client.id}.", e)
          writeControlError(count, client, sw, e)
      }
    }

    new PrintWriter(s"$basedir/summary.json") {write(Json.prettyPrint(Json.toJson(summaries))); close()}
    writeSummaryCsv(summaries)

  } finally {
    driver.quit()
    service.stop()
  }

  def writeSummaryCsv(summaries: ListBuffer[ClientSummary]): Unit = {
    val summaryFile = new File(s"$basedir/summary.csv")
    writeSummaryCsv(summaryFile, summaries)

    val grouped = summaries.groupBy(cs => cs.client.clinician.getOrElse(""))
    grouped.foreach(g => {
      val file = new File(s"$basedir/${g._1}/${g._1} - summary.csv")
      writeSummaryCsv(file, g._2)
    })
  }

  def writeSummaryCsv(dest: File, summaries: ListBuffer[ClientSummary]): Unit = {
    val pw = new PrintWriter(new FileWriter(dest, true))
    pw.println("id,active,clinician,short,first,middle,last,birth,gender,marital,ssn,diagcode,address1,address2,city,state,zip,cell,work,home,email,emailfreq,smsfreq,emergname,emergphone,emergcomments,comments")

    for (summary <- summaries) {
      pw.print(Some(summary.client.id).getForCsv())
      pw.print(Some(s"${summary.isActiveAfter}").getForCsv())
      pw.print(summary.client.clinician.getForCsv())
      pw.print(summary.general.shortname.getForCsv())
      pw.print(summary.general.first.getForCsv())
      pw.print(summary.general.middle.getForCsv())
      pw.print(summary.general.last.getForCsv())
      pw.print(summary.general.getBirthDate.getForCsv())
      pw.print(summary.general.gender.getForCsv())
      pw.print(summary.general.marital.getForCsv())
      pw.print(summary.general.ssn.getForCsv())
      pw.print(summary.treatmentPlan.flatMap(_.getIcd9Codes).getForCsv())
      pw.print(summary.general.address1.getForCsv())
      pw.print(summary.general.address2.getForCsv())
      pw.print(summary.general.city.getForCsv())
      pw.print(summary.general.state.getForCsv())
      pw.print(summary.general.zip.getForCsv())
      pw.print(summary.contact.cell.getForCsv())
      pw.print(summary.contact.work.getForCsv())
      pw.print(summary.contact.home.getForCsv())
      pw.print(summary.contact.email.getForCsv())
      pw.print(summary.contact.emails.getForCsv())
      pw.print(summary.contact.sms.getForCsv())
      pw.print(summary.contact.emergencyName.getForCsv())
      pw.print(summary.contact.emergencyPhone.getForCsv())
      pw.print(summary.contact.emergencyComments.getForCsv())
      pw.print(summary.general.comments.getForCsv(false))
      pw.println()
    }

    pw.flush()
    pw.close()
  }

  def writeControlSuccess(count: Int, client: Client, isActiveBefore: Boolean, isActiveAfter: Boolean, sw: Stopwatch, general: ClientGeneral, contact: ClientContact, provider: ClientProvider,
                          billRecords: Seq[BillingRecord], noteRecords: Seq[NoteRecord],
                          apptRecords: Seq[AppointmentRecord]): Unit = {
    val pw = new PrintWriter(new FileWriter(controlFile, true))
    pw.println(s"$count, ${client.id}, $isActiveBefore, $isActiveAfter, ${sw.elapsed(TimeUnit.SECONDS)}, success, ${billRecords.length}, ${noteRecords.length}, ${apptRecords.length}, ")
    pw.flush()
    pw.close()
  }

  def writeControlError(count: Int, client: Client, sw: Stopwatch, e: Exception): Unit = {
    val pw = new PrintWriter(new FileWriter(controlFile, true))
    pw.println(s"$count ${client.id}, , , ${sw.elapsed(TimeUnit.SECONDS)}, error, 0, 0, 0, ${e.getMessage}" )
    pw.flush()
    pw.close()
  }

  def buildClientList(file: File): Seq[Client] = {
    implicit val clientReads = Json.reads[Client]
    implicit val searchReads = Json.reads[Search]

    val json = Json.parse(Source.fromFile(file).getLines().mkString)
    val jsResult = Json.fromJson[Search](json)
    jsResult match {
      case JsSuccess(s: Search, _) => s.results
      case e: JsError => throw new Exception(e.toString)
    }
  }

  def login(username: String, password: String): Unit = {
    driver.get("https://office.mhpoffice.com/office/login")

    driver.fillField("username", username)
    driver.fillField("password", password)

    val loginButton = driver.findElementById("loginButton______")
    loginButton.click()
  }

  private def getClients(clients: Seq[Client]): Seq[Client] = {
    if (allClients ) {
      val include = Source.fromFile("/Volumes/USB DISK/include.txt").getLines().toSeq
      clients.filter(c => include.contains(c.clinician.getOrElse("")))
    } else {
      val test = Source.fromFile("/Volumes/USB DISK/test.txt").getLines().toSeq
      clients.filter(c => test.contains(c.id))
    }
  }

  private def printStats(clients: Seq[Client]) = {
    val exclude = Source.fromFile("/Volumes/USB DISK/exclude.txt").getLines().toSeq
    val include = Source.fromFile("/Volumes/USB DISK/include.txt").getLines().toSeq
    val test = Source.fromFile("/Volumes/USB DISK/test.txt").getLines().toSeq

    println(exclude.length)
    println(include.length)
    println(test.length)

    val (excluded, notexcluded) = clients.partition(c => exclude.contains(c.clinician.getOrElse("")))
    val (included, notincluded) = clients.partition(c => include.contains(c.clinician.getOrElse("")))
    val totest = clients.filter(c => test.contains(c.id))

    println(notexcluded.length)
    println(excluded.length)
    println(notincluded.length)
    println(included.length)
    println(totest.length)

    val excc = excluded.groupBy(c => c.clinician.getOrElse(""))
    val nexcc = notexcluded.groupBy(c => c.clinician.getOrElse(""))
    val incc = included.groupBy(c => c.clinician.getOrElse(""))
    val nincc = notincluded.groupBy(c => c.clinician.getOrElse(""))

    println(s"excluded ${excc.keys.size}")
    println(s"not excluded ${nexcc.keys.size}")
    println(s"included ${incc.keys.size}")
    println(s"not included ${nincc.keys.size}")

    println("Excluded ****************************************")
    excc.toList.sortWith(_._1 < _._1).foreach(k => println(s"${k._1} - ${k._2.length}"))

    println("Not excluded ****************************************")
    nexcc.toList.sortWith(_._1 < _._1).foreach(k => println(s"${k._1} - ${k._2.length}"))

    println("Included ****************************************")
    incc.toList.sortWith(_._1 < _._1).foreach(k => println(s"${k._1} - ${k._2.length}"))

    println("Not included ****************************************")
    nincc.toList.sortWith(_._1 < _._1).foreach(k => println(s"${k._1} - ${k._2.length}"))

    val blank = clients.filter(c => c.clinician.getOrElse("") == "")
    println(blank.length)

    println("Blank **************************************")
    blank.map(c => s"${c.id} - ${c.name}").foreach(println)

    throw new Exception("stop")
  }
}

case class ClientSummary(client: Client, isActiveBefore: Boolean, isActiveAfter: Boolean, general: ClientGeneral, provider: ClientProvider, contact: ClientContact, treatmentPlan: Option[TreatmentPlan])