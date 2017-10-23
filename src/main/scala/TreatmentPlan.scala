import java.io.{File, PrintWriter}

import org.openqa.selenium.remote.RemoteWebDriver
import play.api.libs.json.{JsError, JsSuccess, Json}

import scala.io.Source

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
}

object TreatmentPlan {
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

  def downloadTreatmenPlan(driver: RemoteWebDriver, client: Client): Option[TreatmentPlan] = {
    val clientDir = ClientDir.get(client)
    val file = new File(s"$clientDir/treatment_plan.json")
    val url = s"https://office.mhpoffice.com/office/clients/treatment_plan?client=${client.id}"
    val download = FileDownload(file, url, Some(client.id))

    FileDownloader.downloadFile(driver, download)

    if (file.exists()) {
      val json = Json.parse(scala.io.Source.fromFile(file).getLines().mkString)
      val pretty = Json.prettyPrint(json)
      new PrintWriter(file) {write(pretty); close()}

      Some(buildOjbect(file))
    } else {
      None
    }
  }
}
