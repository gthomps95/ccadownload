import java.io.{File, FileWriter, PrintWriter}
import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

import com.typesafe.scalalogging.LazyLogging
import org.openqa.selenium.remote.RemoteWebDriver

import scala.io.Source
import StringUtils._
import com.google.common.base.Stopwatch
import play.api.libs.json._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object Program extends App with LazyLogging {
  if (args.length < 2)
    throw new Exception("username password threadCount clientCount includefile printStats")

  val username = args(0)
  val password = args(1)
  val threadCount = if (args.length > 2) args(2).toInt else 1
  val clientCount = if (args.length > 3) args(3).toInt else 10000
  val includeFile = if (args.length > 4) Some(args(4)) else None
  val printStats = if (args.length > 5) args(5).toBoolean else false

  val downloadAllData = true
  val downloadApptData = true
  val downloadApptComments = true

  val ex = java.util.concurrent.Executors.newFixedThreadPool(threadCount)
  implicit val ec = ExecutionContext.fromExecutor(ex)

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
  val basedir = s"${System.getProperty("user.home")}/CCA/download_$now"
  new File(basedir).mkdirs()

  var allAppt = Seq[AppointmentRecord]()
  var allBill = Seq[BillingRecord]()
  var allNotes = Seq[NoteRecord]()

  val controlFile = new File(s"$basedir/control.csv")
  ClientDir.setBasedir(basedir)

  execute(username, password)

  private def execute(username: String, password: String) = {
    implicit val pool = new DriverPool(username, password, s"${System.getProperty("user.home")}/bin/chromedriver")
    try {
      val sw = Stopwatch.createStarted()

      var clients = ClientList.download(basedir)

      if (printStats) printStatistics(clients)

      clients = filterClients(clients)
      logger.info(s"${clients.length}")

      /*
      val ids = Array("PPT21268")
      clients = clients.filter(c => ids.contains(c.id))
      */

      val summaryFutures = for (client <- scala.util.Random.shuffle(clients).take(clientCount).zipWithIndex)
        yield Future { executeClient(client._1, client._2 + 1) }

      val summaries = Await.result(Future.sequence(summaryFutures), Duration.Inf)

      new PrintWriter(s"$basedir/summary.json") {
        write(Json.prettyPrint(Json.toJson(summaries))); close()
      }

      writeSummaryCsv(summaries.filter(_.isSuccess))
      BillingRecordOutput.output(new File(s"$basedir/all_billing.csv"), allBill)
      NoteRecordOutput.output(new File(s"$basedir/all_notes.csv"), allNotes)
      AppointmentRecordOutput.output(new File(s"$basedir/all_appts.csv"), allAppt)

      sw.stop()
      logger.info(s"${summaries.length} executed in ${sw.elapsed(TimeUnit.SECONDS)} seconds.")
    } finally {
      pool.quitAll()
      ex.shutdown()
    }
  }

  private def executeClient(client: Client, count: Int)(implicit pool: DriverPool): ClientSummary = {
    val clientDir = ClientDir.get(client)
    val sw = Stopwatch.createStarted()
    var driverO: Option[RemoteWebDriver] = None
    val summary =
      try {
        driverO = Some(pool.get)
        val driver = driverO.get
        logger.info(s"Starting ${client.id} - $count")

        val isActiveBefore = ClientActivate.isActive(driver, client)
        if (!isActiveBefore)
          ClientActivate.activate(driver, client)

        val general = ClientGeneralBuilder.build(driver, client)
        new PrintWriter(s"$clientDir/general.json") {
          write(Json.prettyPrint(Json.toJson(general))); close()
        }

        val contact = ClientContactBuilder.build(driver, client)
        new PrintWriter(s"$clientDir/contact.json") {
          write(Json.prettyPrint(Json.toJson(contact))); close()
        }

        val provider = ClientProviderBuilder.build(driver, client)
        new PrintWriter(s"$clientDir/provider.json") {
          write(Json.prettyPrint(Json.toJson(provider))); close()
        }

        val billRecords = if (downloadAllData) BillingRecordBuilder.build(driver, client) else List[BillingRecord]()
        BillingRecordDownloadPdf.download(driver, billRecords)
        new PrintWriter(s"$clientDir/billing.json") {
          write(Json.prettyPrint(Json.toJson(billRecords))); close()
        }
        BillingRecordOutput.output(new File(s"$clientDir/billing.csv"), billRecords)
        allBill = allBill ++ billRecords

        var noteRecords = if (downloadAllData) NoteRecordBuilder.build(driver, client) else Seq[NoteRecord]()
        noteRecords = NoteRecordDownloader.download(driver, noteRecords)
        new PrintWriter(s"$clientDir/notes.json") {
          write(Json.prettyPrint(Json.toJson(noteRecords))); close()
        }
        NoteRecordOutput.output(new File(s"$clientDir/notes.csv"), noteRecords)
        allNotes = allNotes ++ noteRecords

        val apptRecords = if (downloadAllData || downloadApptData) AppointmentRecordBuilder.build(driver, client, downloadApptComments) else Seq[AppointmentRecord]()
        new PrintWriter(s"$clientDir/appt.json") {
          write(Json.prettyPrint(Json.toJson(apptRecords))); close()
        }
        AppointmentRecordOutput.output(new File(s"$clientDir/appointments.csv"), apptRecords)
        allAppt = allAppt ++ apptRecords

        val treatmentPlans = TreatmentPlan.downloadTreatmenPlans(driver, client)
        TreatmentPlanRecordOutput.output(new File(s"$clientDir/treatment_plans.csv"), treatmentPlans)

        if (!isActiveBefore)
          ClientActivate.deactivate(driver, client)
        val isActiveAfter = ClientActivate.isActive(driver, client)

        sw.stop()

        ClientSummary(client, Some(isActiveBefore), Some(isActiveAfter), Some(general), Some(provider),
          Some(contact), treatmentPlans, Some(billRecords), Some(noteRecords), Some(apptRecords), isSuccess = true, None, sw.elapsed(TimeUnit.SECONDS))
      } catch {
        case e: Exception =>
          sw.stop()
          logger.error(s"Error in client ${client.id}.", e)
          ClientSummary(client, None, None, None, None, None, Seq(None), None, None, None, isSuccess = false, Some(e.getMessage), sw.elapsed(TimeUnit.SECONDS))
      } finally {
        driverO.foreach(pool.release)
      }

    writeControl(count, summary)
    summary
  }

  private def writeSummaryCsv(summaries: Seq[ClientSummary]): Unit = {
    val summaryFile = new File(s"$basedir/summary.csv")
    writeSummaryCsv(summaryFile, summaries)

    val grouped = summaries.groupBy(cs => cs.client.clinician.getOrElse("blank"))
    grouped.foreach(g => {
      val file = new File(s"$basedir/${g._1}/${g._1} - summary.csv")
      writeSummaryCsv(file, g._2)
    })
  }
  private def writeSummaryCsv(dest: File, summaries: Seq[ClientSummary]): Unit = {
    val pw = new PrintWriter(new FileWriter(dest, true))
    pw.println("id,active,clinician,short,first,middle,last,birth,gender,marital,ssn,diagcode,lastappt,address1,address2,city,state,zip,cell,work,home,email,emailfreq,smsfreq,emergname,emergphone,emergcomments,comments")

    for (summary <- summaries.filter(_.isSuccess)) {
      pw.print(Some(summary.client.id).getForCsv())
      pw.print(Some(summary.isActiveAfter.getOrElse(false).toString).getForCsv())
      pw.print(Some(summary.client.clinician.getOrElse("blank")).getForCsv())
      pw.print(summary.general.get.shortname.getForCsv())
      pw.print(summary.general.get.first.getForCsv())
      pw.print(summary.general.get.middle.getForCsv())
      pw.print(summary.general.get.last.getForCsv())
      pw.print(summary.general.get.getBirthDate.getForCsv())
      pw.print(summary.general.get.gender.getForCsv())
      pw.print(summary.general.get.marital.getForCsv())
      pw.print(summary.general.get.ssn.getForCsv())
      pw.print(summary.treatmentPlans.flatten.headOption.flatMap(_.getIcd9Codes).getForCsv())
      pw.print(summary.lastApptDate.map(d => d.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))).getForCsv())
      pw.print(summary.general.get.address1.getForCsv())
      pw.print(summary.general.get.address2.getForCsv())
      pw.print(summary.general.get.city.getForCsv())
      pw.print(summary.general.get.state.getForCsv())
      pw.print(summary.general.get.zip.getForCsv())
      pw.print(summary.contact.get.cell.getForCsv())
      pw.print(summary.contact.get.work.getForCsv())
      pw.print(summary.contact.get.home.getForCsv())
      pw.print(summary.contact.get.email.getForCsv())
      pw.print(summary.contact.get.emails.getForCsv())
      pw.print(summary.contact.get.sms.getForCsv())
      pw.print(summary.contact.get.emergencyName.getForCsv())
      pw.print(summary.contact.get.emergencyPhone.getForCsv())
      pw.print(summary.contact.get.emergencyComments.getForCsv())
      pw.print(summary.general.get.comments.getForCsv(false))
      pw.println()
    }

    pw.flush()
    pw.close()
  }

  private def writeControl(count: Int, summary: ClientSummary) = {
    synchronized {
      val pw = new PrintWriter(new FileWriter(controlFile, true))
      if (summary.isSuccess) {
        pw.println(s"$count, ${summary.client.clinician.getOrElse("blank")}, ${summary.client.id}, ${summary.isActiveBefore.get}, ${summary.isActiveAfter.get}, ${summary.elapsedSeconds}, success, ${summary.billingRecords.get.length}, ${summary.noteRecords.get.length}, ${summary.apptRecords.get.length}, ")
      } else {
        pw.println(s"$count, ${summary.client.clinician.getOrElse("blank")}, ${summary.client.id}, , , ${summary.elapsedSeconds}, error, 0, 0, 0, ${summary.errorMessage.get} ")
      }

      pw.flush()
      pw.close()
    }
  }

  private def filterClients(clients: Seq[Client]): Seq[Client] = {
    includeFile match {
      case None => clients
      case Some(file) =>
        val include = Source.fromFile(file).getLines().toSeq
        clients.filter(c => include.contains(c.clinician.getOrElse("blank")))
    }
  }

  private def printStatistics(clients: Seq[Client]) = {
    val include = Source.fromFile(new File(includeFile.get)).getLines().toSeq

    logger.info(include.length + "")

    val (included, notincluded) = clients.partition(c => include.contains(c.clinician.getOrElse("blank")))

    logger.info(notincluded.length + "")
    logger.info(included.length + "")

    val incc = included.groupBy(c => c.clinician.getOrElse("blank"))
    val nincc = notincluded.groupBy(c => c.clinician.getOrElse("blank"))

    logger.info(s"included ${incc.keys.size}")
    logger.info(s"not included ${nincc.keys.size}")

    logger.info("Included ****************************************")
    incc.toList.sortWith(_._1 < _._1).foreach(k => logger.info(s"${k._1} - ${k._2.length}"))

    logger.info("Not included ****************************************")
    nincc.toList.sortWith(_._1 < _._1).foreach(k => logger.info(s"${k._1} - ${k._2.length}"))

    val blank = clients.filter(c => c.clinician.getOrElse("blank") == "")
    logger.info(blank.length + "")

    logger.info("Blank **************************************")
    blank.map(c => s"${c.id} - ${c.name}").foreach(s => logger.info(s))

    throw new Exception("stop")
  }
}

case class ClientSummary(client: Client, isActiveBefore: Option[Boolean], isActiveAfter: Option[Boolean],
                         general: Option[ClientGeneral], provider: Option[ClientProvider], contact: Option[ClientContact],
                         treatmentPlans: Seq[Option[TreatmentPlan]],
                         billingRecords: Option[Seq[BillingRecord]], noteRecords: Option[Seq[NoteRecord]],
                         apptRecords: Option[Seq[AppointmentRecord]],
                         isSuccess: Boolean, errorMessage: Option[String] = None,
                         elapsedSeconds: Long
                        ) {

  def lastApptDate: Option[LocalDate] = {
    apptRecords match {
      case None => None
      case Some(records) =>
        val filtered = records.filter(ap => ap.apptType == "Past").flatMap(ap => ap.date)

        if (filtered.isEmpty)
          None
        else
          Some(filtered.maxBy(_.toEpochDay))
    }
  }
}
