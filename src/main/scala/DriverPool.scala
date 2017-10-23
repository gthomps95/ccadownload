import java.io.File

import WebDriverUtils._
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.remote.{DesiredCapabilities, RemoteWebDriver}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class DriverPool(username: String, password: String) {

  def quitAll(): Unit = {
    drivers.foreach(_.quit())
    services.foreach(_.stop())
  }

  private val _pool = mutable.Queue[RemoteWebDriver]()
  private val drivers = ListBuffer[RemoteWebDriver]()
  private val services = ListBuffer[ChromeDriverService]()

  private val chromeDriver = new File("/Users/gthompson/bin/chromedriver")

  private def startService() = {
    val service = new ChromeDriverService.Builder()
      .usingDriverExecutable(chromeDriver)
      .usingAnyFreePort()
      .build()

    service.start()
    service
  }

  private def startNewDriver(): RemoteWebDriver = {
    val service = startService()
    services.append(service)
    val result = new RemoteWebDriver(service.getUrl, DesiredCapabilities.chrome())
    login(result)
  }

  private def login(driver: RemoteWebDriver): RemoteWebDriver = {
    driver.get("https://office.mhpoffice.com/office/login")

    driver.fillField("username", username)
    driver.fillField("password", password)

    val loginButton = driver.findElementById("loginButton______")
    loginButton.click()
    driver
  }

  def get: RemoteWebDriver = {
    this.synchronized {
      if (_pool.isEmpty) {
        val newDriver = startNewDriver()
        drivers.append(newDriver)
        _pool.enqueue(newDriver)
      }

      _pool.dequeue()
    }
  }

  def release(driver: RemoteWebDriver): Unit = {
    this.synchronized {
      _pool.enqueue(driver)
    }
  }
}
