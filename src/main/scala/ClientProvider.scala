import WebDriverUtils._
import org.openqa.selenium.remote.RemoteWebDriver

case class ClientProvider(client: Client,
                          clinician: Option[String] = None
                         )

object ClientProviderBuilder {
  def build(driver: RemoteWebDriver, client: Client): ClientProvider = {
    val url = s"https://office.mhpoffice.com/office/client/${client.id}?edit=info"
    driver.checkGetUrl(url)

    var c = driver.getSelectedTextFromXPath("//*[@id=\"assignedClinician-single-field\"]")

    c = c match {
      case None => c
      case Some(s) => Some(s.replace("\t", " ").split(" ").map(_.trim).filter(_.nonEmpty).mkString(" "))
    }

    ClientProvider(client = client, clinician = c)
 }
}