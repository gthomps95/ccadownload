import WebDriverUtils._
import org.openqa.selenium.remote.RemoteWebDriver

case class ClientGeneral(client: Client,
                         first: Option[String] = None,
                         middle: Option[String] = None,
                         last: Option[String] = None,
                         shortname: Option[String],
                         gender: Option[String] = None,
                         dobm: Option[String] = None,
                         dobd: Option[String] = None,
                         doby: Option[String] = None,
                         address1: Option[String] = None,
                         address2: Option[String] = None,
                         city: Option[String] = None,
                         state: Option[String] = None,
                         zip: Option[String] = None,
                         mothersMaiden: Option[String] = None,
                         ssn: Option[String] = None,
                         license: Option[String] = None,
                         marital: Option[String] = None,
                         employment: Option[String] = None,
                         accountNumber: Option[String] = None,
                         comments: Option[String] = None
                        ) {

  def getBirthDate: Option[String] = {
    if (dobm.isEmpty || dobd.isEmpty || doby.isEmpty)
      return None

    if (dobm.get.isEmpty || dobd.get.isEmpty || doby.get.isEmpty)
      return None

    Some(s"${dobm.get}/${dobd.get}/${doby.get}")
  }
}

object ClientGeneralBuilder {
  def build(driver: RemoteWebDriver, client: Client): ClientGeneral = {
    val url = s"https://office.mhpoffice.com/office/client/${client.id}?edit=info"
    driver.checkGetUrl(url)

    val f = driver.getValueFromXPath("//*[@id=\"firstName\"]")
    val m = driver.getValueFromXPath("//*[@id=\"middleName\"]")
    val l = driver.getValueFromXPath("//*[@id=\"lastName\"]")
    val sn = driver.getValueFromXPath("//*[@id=\"shortname\"]")

    val g = driver.getValueFromXPath("//*[@id=\"attr:gender\"]")

    val dobm = driver.getSelectedValueFromXPath("//*[@id=\"e-dob-month\"]")
    val dobd = driver.getSelectedValueFromXPath("//*[@id=\"e-dob-day\"]")
    val doby = driver.getSelectedValueFromXPath("//*[@id=\"e-dob-year\"]")

    val a1 = driver.getValueFromXPath("//*[@id=\"e-address1\"]")
    val a2 = driver.getValueFromXPath("//*[@id=\"e-address2\"]")
    val ci = driver.getValueFromXPath("//*[@id=\"e-city\"]")
    val st = driver.getValueFromXPath("//*[@id=\"e-state\"]")
    val zi = driver.getValueFromXPath("//*[@id=\"e-zip\"]")

    val mm = driver.getValueFromXPath("//*[@id=\"attr:person:mothermaiden\"]")
    val ss = driver.getValueFromXPath("//*[@id=\"attr:person:ssn\"]")
    val dl = driver.getValueFromXPath("//*[@id=\"attr:info:driverslicense\"]")
    val ms = driver.getSelectedValueFromXPath("//*[@id=\"attr:person:maritalStatus\"]")
    val es = driver.getSelectedValueFromXPath("//*[@id=\"attr:person:employment1500\"]")
    val an = driver.getValueFromXPath("//*[@id=\"attr:person:PatientAccountNumber\"]")

    val cm = driver.getValueFromXPath("//*[@id=\"attr:custom:notes\"]")

    ClientGeneral(client = client,
      first = f, middle = m, last = l,
      shortname = sn,
      gender = g,
      dobm = dobm, dobd = dobd, doby = doby,
      address1 = a1, address2 = a2, city = ci, state = st, zip = zi,
      mothersMaiden = mm, ssn = ss, license = dl, marital = ms, employment = es, accountNumber = an,
      comments = cm
    )
  }
}