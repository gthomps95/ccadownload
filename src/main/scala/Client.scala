import java.io.File

case class Client(id: String = "", name: String, clinician: Option[String])

object ClientDir {
  private var basedir = ""

  def setBasedir(basedir: String): Unit = {
    this.basedir = basedir
  }

  def get(client: Client): String = {
    val clientDir = s"$basedir/${client.clinician.getOrElse("blank")}/${client.name} - ${client.id}".trim

    val file = new File(clientDir)
    if (!file.exists())
      file.mkdirs()

    clientDir
  }
}

object ClientScrubber {
  def fixClientNames(clients: Seq[Client]): Seq[Client] = {
    clients.map(c => c.copy(name = c.name.replace("\t", " ").split(" ").map(_.trim).filter(_.nonEmpty).mkString(" ")))
  }

  def fixClinicianNames(clients: Seq[Client]): Seq[Client] = {
    clients.map(c => {
      c.clinician match {
        case None => c
        case Some(s) =>
          c.copy(clinician = Some(s.replace("\t", " ").split(" ").map(_.trim).filter(_.nonEmpty).mkString(" ")))
      }
    })
  }
}