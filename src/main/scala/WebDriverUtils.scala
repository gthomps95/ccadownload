import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.ui.Select

import scala.collection.JavaConverters._

/**
  * Created by gthompson on 10/17/17.
  */

object WebDriverUtils {
  implicit class WebDriverExt(val driver: RemoteWebDriver) {
    def getTextFromXpath(xpath: String): Option[String] = {
      val fields = driver.findElementsByXPath(xpath).asScala
      if (fields.nonEmpty) Some(fields.head.getText) else None
    }

    def getAttributeFromXPath(xpath: String, attr: String): Option[String] = {
      val fields = driver.findElementsByXPath(xpath).asScala
      if (fields.nonEmpty) Some(fields.head.getAttribute(attr)) else None
    }

    def getValueFromXPath(xpath: String): Option[String] = {
      getAttributeFromXPath(xpath, "value")
    }

    def getSelectedValueFromXPath(xpath: String): Option[String] = {
      val fields = driver.findElementsByXPath(xpath).asScala
      if (fields.isEmpty)
        None
      else {
        val select = new Select(fields.head)
        Some(select.getFirstSelectedOption.getAttribute("value"))
      }
    }

    def getSelectedTextFromXPath(xpath: String): Option[String] = {
      val fields = driver.findElementsByXPath(xpath).asScala
      if (fields.isEmpty)
        None
      else {
        val select = new Select(fields.head)
        val opt = select.getFirstSelectedOption
        Some(opt.getAttribute("innerHTML"))
      }
    }

    def fillField(fieldName: String, value: String): Unit = {
      val field = driver.findElementByName(fieldName)
      field.sendKeys(value)
    }

    def checkGetUrl(url: String): Unit = {
      driver.getCurrentUrl match {
        case `url` =>
        case _ => driver.get(url)
      }
    }
  }
}
