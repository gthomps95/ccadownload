import java.io.{BufferedWriter, OutputStreamWriter}
import java.net.{HttpURLConnection, URL}

import WebDriverUtils._
import com.typesafe.scalalogging.LazyLogging
import org.apache.http.message.BasicNameValuePair
import org.openqa.selenium.remote.RemoteWebDriver

import scala.collection.JavaConverters._


/**
  * Created by gthompson on 10/18/17.
  */
object ClientActivate extends LazyLogging {
  def isActive(driver: RemoteWebDriver, client: Client): Boolean = {
    val url = s"https://office.mhpoffice.com/office/client/${client.id}"
    driver.checkGetUrl(url)

    val elements = driver.findElementsByXPath("//*[@id=\"actions-reb\"]/ul/li[2]/a").asScala
    if (elements.isEmpty)
      return true

    elements.head.getText != "Activate Client"
  }

  def activate(driver: RemoteWebDriver, client: Client, numtries: Int = 1): Unit = {
    tryPostActivate(driver, client, "reactivate")
  }

  def deactivate(driver: RemoteWebDriver, client: Client, numtries: Int = 1): Unit = {
    tryPostActivate(driver, client, "deactivate")
  }

  def tryPostActivate(driver: RemoteWebDriver, client: Client, method: String, numtries: Int = 1): Unit = {
    try {
      postActivate(driver, client, method)
    }
    catch {
      case t: Throwable =>
        if (numtries < 5) {
          logger.error(s"$method failed $numtries tries for ${client.id} - ${t.getMessage}, retrying")
          tryPostActivate(driver, client, method, numtries + 1)
        }
        else throw t
    }
  }

  private def postActivate(driver: RemoteWebDriver, client: Client, method: String): Unit = {
    val sessionKey = "_mhpoffice_session"
    val sessionValue = driver.manage().getCookieNamed(sessionKey).getValue
    val sessionCookie = s"$sessionKey=$sessionValue"

    val url = new URL("https://office.mhpoffice.com/api/client/deactivate")
    val connection = url.openConnection().asInstanceOf[HttpURLConnection]

    connection.setReadTimeout(180000)
    connection.setConnectTimeout(180000)
    connection.setRequestMethod("POST")
    connection.setRequestProperty("Cookie", sessionCookie)
    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13) AppleWebKit/604.1.38 (KHTML, like Gecko) Version/11.0 Safari/604.1.38")
    connection.setRequestProperty("Accept-Encoding", "br, gzip, deflate")
    connection.setDoInput(true)
    connection.setDoOutput(true)

    val params = scala.collection.mutable.ListBuffer[BasicNameValuePair]()

    params.append(new BasicNameValuePair("client", client.id))
    params.append(new BasicNameValuePair("m", method))

    val os = connection.getOutputStream
    val writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"))
    writer.write(getQuery(params.toList))
    writer.flush()
    writer.close()

    connection.connect()

    val status = connection.getResponseCode
    if (status != 302 && status != 200) {
      Thread.sleep(2000)

      val active = isActive(driver, client)
      if ((method == "reactivate" && !active) || (method == "deactivate" && active))
        throw new Exception(s"$method failed for ${client.id} status code $status")
    }
  }

  private def getQuery(params: List[BasicNameValuePair]) = {
    val result = new StringBuilder
    var first = true

    for (pair <- params) {
      if (first) first = false
      else result.append("&")

      result.append(java.net.URLEncoder.encode(pair.getName, "UTF-8"))
      result.append("=")
      result.append(java.net.URLEncoder.encode(pair.getValue, "UTF-8"))
    }

    result.toString
  }
}
