import java.io.{File, FileWriter, PrintWriter}
import java.time.format.DateTimeFormatter

import com.typesafe.scalalogging.LazyLogging
import org.openqa.selenium.remote.RemoteWebDriver
import play.api.libs.json.{JsError, JsSuccess, Json}

import scala.io.Source

import StringUtils._


case class History(id: Int, summary: String, date: String, shown: Boolean)
case class DiagnosisCode(id: Int, name: Option[String], comment: Option[String], mhp_id: Option[String], icd9: Option[String], icd10: Option[String], section: Option[String], subsection: Option[String])
case class Diagnosis(rows: Seq[DiagnosisCode])
case class AllDiagnosis(diagnosis: Option[Diagnosis])

case class TreatmentPlan(id: Option[Int], client_id: Option[String], treatment_plan: AllDiagnosis, history: Seq[History]) {
  def getIcd9Codes: Option[String] = {
    treatment_plan.diagnosis match {
      case None => None
      case Some(d) =>
        if (d.rows.isEmpty)
          None
        else
          Some(d.rows.filter(r => r.icd9.nonEmpty).map(r => r.icd9.get).mkString(" "))
    }
  }

  def getIcd10Codes: Option[String] = {
    treatment_plan.diagnosis match {
      case None => None
      case Some(d) =>
        if (d.rows.isEmpty)
          None
        else
          Some(d.rows.filter(r => r.icd10.nonEmpty).map(r => r.icd10.get).mkString(" "))
    }
  }

  def getComment: Option[String] = {
    treatment_plan.diagnosis match {
      case None => None
      case Some(d) =>
        if (d.rows.isEmpty)
          None
        else
          Some(d.rows.filter(r => r.comment.nonEmpty).map(r => r.comment.get).mkString(" "))
    }
  }

  def getName: Option[String] = {
    treatment_plan.diagnosis match {
      case None => None
      case Some(d) =>
        if (d.rows.isEmpty)
          None
        else
          Some(d.rows.filter(r => r.name.nonEmpty).map(r => r.name.get).mkString(" "))
    }
  }
  def getSection: Option[String] = {
    treatment_plan.diagnosis match {
      case None => None
      case Some(d) =>
        if (d.rows.isEmpty)
          None
        else
          Some(d.rows.filter(r => r.section.nonEmpty).map(r => r.section.get).mkString(" "))
    }
  }

  def getSubSection: Option[String] = {
    treatment_plan.diagnosis match {
      case None => None
      case Some(d) =>
        if (d.rows.isEmpty)
          None
        else
          Some(d.rows.filter(r => r.subsection.nonEmpty).map(r => r.subsection.get).mkString(" "))
    }
  }

  def getDate: Option[String] = {
    val h = history.find(h => h.id == id.getOrElse(0))
    h match {
      case None => None
      case Some(h1) => Some(h1.date)
    }
  }
}

object TreatmentPlan extends LazyLogging {

  def buildOjbect(file: File): TreatmentPlan = {
    implicit val historyFormat = Json.reads[History]
    implicit val diagnosisCodeFormat = Json.reads[DiagnosisCode]
    implicit val diagnosisFormat = Json.reads[Diagnosis]
    implicit val allDiagnosisFormat = Json.reads[AllDiagnosis]
    implicit val treatmentPlanFormat = Json.reads[TreatmentPlan]

    val json = Json.parse(Source.fromFile(file).getLines().mkString)
    val jsResult = Json.fromJson[TreatmentPlan](json)
    jsResult match {
      case JsSuccess(tp: TreatmentPlan, _) => tp
      case e: JsError => throw new Exception(e.toString)
    }
  }

  def downloadTreatmenPlans(driver: RemoteWebDriver, client: Client): Seq[Option[TreatmentPlan]] = {
    val latest = downloadTreatmenPlan(driver, client)

    latest match {
      case None => Seq()
      case Some(tp) =>
        tp.history.map(h => downloadTreatmenPlan(driver, client, Some(h.id)))
    }
  }

  def downloadTreatmenPlan(driver: RemoteWebDriver, client: Client, id: Option[Int] = None): Option[TreatmentPlan] = {
    val clientDir = ClientDir.get(client)

    val file = id match {
      case None => new File(s"$clientDir/treatment_plan.json")
      case Some(theId) => new File(s"$clientDir/treatment_plan_$theId.json")
    }

    val baseurl = s"https://office.mhpoffice.com/office/clients/treatment_plan?client=${client.id}"

    val url = id match {
      case None => baseurl
      case Some(theId) => s"$baseurl&id=$theId"
    }

    val download = FileDownload(file, url, Some(client.id))

    val status = FileDownloader.downloadFile(driver, download)

    if (!status) logger.error(s"Treatment plan download failure for ${client.id}.")

    if (status && file.exists()) {
      val json = Json.parse(scala.io.Source.fromFile(file).getLines().mkString)
      val pretty = Json.prettyPrint(json)
      new PrintWriter(file) {write(pretty); close()}

      Some(buildOjbect(file))
    } else {
      None
    }
  }
}

object TreatmentPlanRecordOutput {
  def output(dest: File, records: Seq[Option[TreatmentPlan]]): Unit = {
    val pw = new PrintWriter(new FileWriter(dest, true))
    pw.println("id,icd9,icd10,section,subsection,name,comment,date")

    for (record <- records.flatten) {
      pw.print(Some(record.id.getOrElse(0).toString).getForCsv())
      pw.print(record.getIcd9Codes.getForCsv())
      pw.print(record.getIcd10Codes.getForCsv())
      pw.print(record.getSection.getForCsv())
      pw.print(record.getSubSection.getForCsv())
      pw.print(record.getName.getForCsv())
      pw.print(record.getComment.getForCsv())
      pw.print(record.getDate.getForCsv())
      pw.println()
    }

    pw.flush()
    pw.close()
  }
}

