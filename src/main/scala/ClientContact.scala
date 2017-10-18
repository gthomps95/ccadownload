import WebDriverUtils._
import org.openqa.selenium.remote.RemoteWebDriver

case class ClientContact(client: Client,
                         cell: Option[String] = None,
                         work: Option[String] = None,
                         home: Option[String] = None,
                         email: Option[String] = None,
                         emails: Option[String] = None,
                         sms: Option[String] = None,
                         emergencyName: Option[String] = None,
                         emergencyPhone: Option[String] = None,
                         emergencyComments: Option[String] = None
                        )

object ClientContactBuilder {

  def build(driver: RemoteWebDriver, client: Client): ClientContact = {
    val url = s"https://office.mhpoffice.com/office/client/${client.id}?edit=info"
    driver.checkGetUrl(url)

    val c = driver.getValueFromXPath("//*[@id=\"attr:info:phone:cell\"]")
    val w = driver.getValueFromXPath("//*[@id=\"attr:info:phone:work\"]")
    val h = driver.getValueFromXPath("//*[@id=\"attr:info:phone:home\"]")
    val e = driver.getValueFromXPath("//*[@id=\"attr:info:email:default\"]")

    val es = driver.getSelectedValueFromXPath("//*[@id=\"attr:info:appointment:reminderTime\"]")
    val ss = driver.getSelectedValueFromXPath("//*[@id=\"attr:info:appointment:reminderSms\"]")
    val en = driver.getValueFromXPath("//*[@id=\"attr:person:emergencyContact:name\"]")
    val ep = driver.getValueFromXPath("//*[@id=\"attr:person:emergencyContact:phone\"]")
    val ec = driver.getValueFromXPath("//*[@id=\"attr:person:emergencyContact:comment\"]")

    ClientContact(client = client, cell = c, work = w, home = h, email = e, emails = es, sms = ss,
      emergencyName = en, emergencyPhone = ep, emergencyComments = ec
    )
  }
}