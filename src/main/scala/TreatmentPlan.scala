import java.io.{File, PrintWriter}

import org.openqa.selenium.remote.RemoteWebDriver
import play.api.libs.json.Json

object TreatmentPlan {
  def downloadTreatmenPlan(driver: RemoteWebDriver, client: Client): Unit = {
    val clientDir = ClientDir.get(client)
    val file = new File(s"$clientDir/treatment_plan.json")
    val url = s"https://office.mhpoffice.com/office/clients/treatment_plan?client=${client.id}"
    val download = FileDownload(file, url)

    FileDownloader.downloadFile(driver, download)

    if (file.exists()) {
      val json = Json.parse(scala.io.Source.fromFile(file).getLines().mkString)
      val pretty = Json.prettyPrint(json)
      new PrintWriter(file) {write(pretty); close()}
    }
  }
}
