import java.io.File

import org.openqa.selenium.remote.RemoteWebDriver
import play.api.libs.json.{JsError, JsSuccess, Json}

import scala.io.Source

/**
  * Created by gthompson on 10/23/17.
  */
object ClientList {

  def download(basedir: String)(implicit pool: DriverPool): Seq[Client] = {
    var driverO: Option[RemoteWebDriver] = None
    try {
      driverO = Some(pool.get)
      val driver = driverO.get
      val file = new File(s"$basedir/client_list.json")
      val url = "https://office.mhpoffice.com/api/V1/search.json?t=client-all&start=0&count=5000&q="
      val download = FileDownload(file, url, None)

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
    finally {
      driverO.foreach(pool.release)
    }
  }
}
