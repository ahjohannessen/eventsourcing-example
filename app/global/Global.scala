package global

import dev.example.eventsourcing.server.Appserver
import play.api._

object Global extends GlobalSettings {
  val appserver: Appserver = Appserver.boot()
}
