import java.io._
import java.net.{HttpURLConnection, URL}

import com.typesafe.scalalogging.LazyLogging
import org.openqa.selenium.remote.RemoteWebDriver

case class FileDownload(destFile: File, url: String)

object FileDownloader extends LazyLogging {
  def downloadFile(driver: RemoteWebDriver, download: FileDownload): Boolean = {
    var out: Option[OutputStream] = None
    var in: Option[InputStream] = None

    try {
      val sessionKey = "_mhpoffice_session"
      val sessionValue = driver.manage().getCookieNamed(sessionKey).getValue
      val sessionCookie = s"$sessionKey=$sessionValue"

      val url = new URL(download.url)
      val connection = url.openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("GET")

      if (download.url.startsWith("https://office.mhpoffice.com")) connection.setRequestProperty("Cookie", sessionCookie)

      connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13) AppleWebKit/604.1.38 (KHTML, like Gecko) Version/11.0 Safari/604.1.38")
      connection.setRequestProperty("Accept-Encoding", "br, gzip, deflate")

      val status = connection.getResponseCode
      if (status != 200)
        throw new Exception(s"File download returned status $status, ${download.url}")

      //val cd = connection.getHeaderField("Content-Disposition")
      in = Some(connection.getInputStream)

      val is = in.get

      out = Some(new BufferedOutputStream(new FileOutputStream(download.destFile, false)))
      val os = out.get

      val buffer = new Array[Byte](4096)

      Iterator
        .continually(is.read(buffer))
        .takeWhile(_ != -1)
        .foreach(read => os.write(buffer, 0, read))

      true
    }
    catch {
      case e: Exception =>
        logger.error("Error in file download", e)
        false
    }
    finally {
      out match {
        case None =>
        case Some(o) =>
          o.flush()
          o.close()
      }

      in match {
        case None =>
        case Some(i) =>
          i.close()
      }
    }
  }
}
