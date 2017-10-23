import java.io.File

import org.openqa.selenium.remote.RemoteWebDriver
import play.api.libs.json.{JsError, JsSuccess, Json}

import scala.io.Source

/**
  * Created by gthompson on 10/23/17.
  */
object ClientList {

  def download(driver: RemoteWebDriver, basedir: String): Seq[Client] = {
    val file = new File(s"${basedir}/client_list.json")
    val url = s"https://office.mhpoffice.com/api/V1/search.json?t=client-all&start=0&count=4571&q="
    val download = FileDownload(file, url)

    FileDownloader.downloadFile(driver, download)

    implicit val clientReads = Json.reads[Client]
    implicit val searchReads = Json.reads[Search]

    val json = Json.parse(Source.fromFile(file).getLines().mkString)
    val jsResult = Json.fromJson[Search](json)
    var clients = jsResult match {
      case JsSuccess(s: Search, _) => s.results
      case e: JsError => throw new Exception(e.toString)
    }

    clients = ClientScrubber.fixClinicianNames(clients)
    clients = ClientScrubber.fixClientNames(clients)
    clients
  }
}
