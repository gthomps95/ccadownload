import java.io.{File, FileWriter, PrintWriter}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

import com.typesafe.scalalogging.LazyLogging
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.remote.{DesiredCapabilities, RemoteWebDriver}

import scala.io.Source
import WebDriverUtils._
import com.google.common.base.Stopwatch
import play.api.libs.json._

import scala.collection.mutable.ListBuffer

object Program extends App with LazyLogging {
  implicit val clientFormat = Json.format[Client]
  implicit val generalFormat = Json.writes[ClientGeneral]
  implicit val contactFormat = Json.writes[ClientContact]
  implicit val providerFormat = Json.writes[ClientProvider]
  implicit val billFormat = Json.writes[BillingRecord]
  implicit val noteFormat = Json.writes[NoteRecord]
  implicit val apptFormat = Json.writes[AppointmentRecord]
  implicit val summaryFormat = Json.writes[ClientSummary]

  val now = LocalDateTime.now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
  val basedir = s"/Volumes/USB DISK/CCA/download_$now"
  val controlFile = new File(s"$basedir/control.csv")
  ClientDir.setBasedir(basedir)

  var clients = buildClientList(new File("/Volumes/USB DISK/CCA_Data.json"))
  clients = ClientScrubber.fixClinicianNames(clients)
  clients = ClientScrubber.fixClientNames(clients)
  println(clients.length)

  clients = getClients
  //printStats

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

    //for (client <- clients.filter(c => c.id == "PPT21885")) {
    //for (client <- clients.filter(c => c.id == "PPT21885")) {
    //for (client <- clients.filter(c => c.id == "PPT12721")) {
    //for (client <- clients.filter(c => c.id == "PPT5240")) {
    //for (client <- clients.filter(c => c.id == "PPT21226")) {
    //for (client <- clients.filter(c => c.id == "PPT4810")) {
    //for (client <- clients.filter(c => c.id == "PPT18042")) {
    for (client <- clients.filter(c => !c.name.isEmpty).take(100)) {
      val clientDir = ClientDir.get(client)

      try {
        println(client.id)

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

        //val billRecords = BillingRecordBuilder.build(driver, client)
        //BillingRecordDownloadPdf.download(driver, billRecords)
        //new PrintWriter(s"$clientDir/billing.json") {write(Json.prettyPrint(Json.toJson(billRecords))); close()}

        //var noteRecords = NoteRecordBuilder.build(driver, client)
        //noteRecords = NoteRecordDownloader.download(driver, noteRecords)
        //new PrintWriter(s"$clientDir/notes.json") {write(Json.prettyPrint(Json.toJson(noteRecords))); close()}

        //val apptRecords = AppointmentRecordBuilder.build(driver, client)
        //new PrintWriter(s"$clientDir/appt.json") {write(Json.prettyPrint(Json.toJson(apptRecords))); close()}

        TreatmentPlan.downloadTreatmenPlan(driver, client)

        if (!isActiveBefore)
          ClientActivate.deactivate(driver, client)
        val isActiveAfter = ClientActivate.isActive(driver, client)

        sw.stop()

        summaries.append(ClientSummary(client, isActiveBefore, isActiveAfter, general, provider, contact))
        //writeControlSuccess(client, sw, general, contact, provider, billRecords, noteRecords, apptRecords)
        writeControlSuccess(client, isActiveBefore, isActiveAfter, sw, general, contact, provider, Seq[BillingRecord](), Seq[NoteRecord](), Seq[AppointmentRecord]())
      }
      catch {
        case e: Exception =>
          sw.stop()
          logger.error(s"Error in client ${client.id}.", e)
          writeControlError(client, sw, e)
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
    val pw = new PrintWriter(new FileWriter(summaryFile, true))
    pw.println("id,clinician,short,first,middle,last,birth,marital,address1,address2,city,state,zip,cell,work,home,email,emailfreq,smsfreq,emergname,emergphone,emergcomments,comments")

    for (summary <- summaries) {
      pw.print(getForCsv(Some(summary.client.id)))
      pw.print(getForCsv(summary.client.clinician))
      pw.print(getForCsv(summary.general.shortname))
      pw.print(getForCsv(summary.general.first))
      pw.print(getForCsv(summary.general.middle))
      pw.print(getForCsv(summary.general.last))
      pw.print(getForCsv(summary.general.getBirthDate))
      pw.print(getForCsv(summary.general.marital))
      pw.print(getForCsv(summary.general.address1))
      pw.print(getForCsv(summary.general.address2))
      pw.print(getForCsv(summary.general.city))
      pw.print(getForCsv(summary.general.state))
      pw.print(getForCsv(summary.general.zip))
      pw.print(getForCsv(summary.contact.cell))
      pw.print(getForCsv(summary.contact.work))
      pw.print(getForCsv(summary.contact.home))
      pw.print(getForCsv(summary.contact.email))
      pw.print(getForCsv(summary.contact.emails))
      pw.print(getForCsv(summary.contact.sms))
      pw.print(getForCsv(summary.contact.emergencyName))
      pw.print(getForCsv(summary.contact.emergencyPhone))
      pw.print(getForCsv(summary.contact.emergencyComments))
      pw.print(getForCsv(summary.general.comments))
      pw.println()
    }

    pw.flush()
    pw.close()
  }

  private def getForCsv(value: Option[String], includeComma: Boolean = true): String = {
    val text = value match {
      case None => ""
      case Some(s) => s.replace(",", " ").replace("\n", " ").replace("\t", " ").split(" ").map(_.trim).filter(!_.isEmpty).mkString(" ")
    }

    text + (if (includeComma) "," else "")
  }

  def writeControlSuccess(client: Client, isActiveBefore: Boolean, isActiveAfter: Boolean, sw: Stopwatch, general: ClientGeneral, contact: ClientContact, provider: ClientProvider,
                          billRecords: Seq[BillingRecord], noteRecords: Seq[NoteRecord],
                          apptRecords: Seq[AppointmentRecord]): Unit = {
    val pw = new PrintWriter(new FileWriter(controlFile, true))
    pw.println(s"${client.id}, $isActiveBefore, $isActiveAfter, ${sw.elapsed(TimeUnit.SECONDS)}, success, ${billRecords.length}, ${noteRecords.length}, ${apptRecords.length}, ")
    pw.flush()
    pw.close()
  }

  def writeControlError(client: Client, sw: Stopwatch, e: Exception): Unit = {
    val pw = new PrintWriter(new FileWriter(controlFile, true))
    pw.println(s"${client.id}, , , ${sw.elapsed(TimeUnit.SECONDS)}, error, 0, 0, 0, ${e.getMessage}" )
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

  private def getClients: Seq[Client] = {
    val include = Source.fromFile("/Volumes/USB DISK/include.txt").getLines().toSeq
    clients.filter(c => include.contains(c.clinician.getOrElse("")))

    //val test = Source.fromFile("/Volumes/USB DISK/test.txt").getLines().toSeq
    //clients.filter(c => test.contains(c.id))
  }

  private def printStats = {
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

    val excc = scala.collection.mutable.Set[String]()
    excluded.foreach(c => excc.add(c.clinician.getOrElse("")))

    val nexcc = scala.collection.mutable.Set[String]()
    notexcluded.foreach(c => nexcc.add(c.clinician.getOrElse("")))

    val incc = scala.collection.mutable.Set[String]()
    included.foreach(c => incc.add(c.clinician.getOrElse("")))

    val nincc = scala.collection.mutable.Set[String]()
    notincluded.foreach(c => nincc.add(c.clinician.getOrElse("")))

    println(s"excluded ${excc.size}")
    println(s"not excluded ${nexcc.size}")
    println(s"included ${incc.size}")
    println(s"not included ${nincc.size}")

    println("Excluded ****************************************")
    excc.foreach(println)

    println("Not excluded ****************************************")
    nexcc.foreach(println)

    println("Included ****************************************")
    incc.foreach(println)

    println("Not included ****************************************")
    nincc.foreach(println)

    val blank = clients.filter(c => c.clinician.getOrElse("") == "")
    println(blank.length)

    println("Blank **************************************")
    blank.map(c => s"${c.id} - ${c.name}").foreach(println)

    throw new Exception("stop")
  }
}

case class ClientSummary(client: Client, isActiveBefore: Boolean, isActiveAfter: Boolean, general: ClientGeneral, provider: ClientProvider, contact: ClientContact)