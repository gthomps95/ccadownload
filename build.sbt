name := "ccadownload"

version := "1.0"

scalaVersion := "2.12.1"

libraryDependencies ++= Seq (
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "org.seleniumhq.selenium" % "selenium-chrome-driver" %"3.6.0",
  "org.seleniumhq.selenium" % "selenium-support" % "3.6.0",
  "com.typesafe.play" %% "play-json" % "2.6.6",
  "commons-io" % "commons-io" % "2.5",
  "joda-time" % "joda-time" % "2.9.9"
)
