package dev.example.eventsourcing.event

import akka.actor._
import akka.dispatch._
import akka.pattern.ask
import akka.util.duration._

class TestEventLog(system: ActorSystem) extends EventLog {
  implicit val timeout = system.settings.ActorTimeout

  val logger = system.actorOf(Props(new Logger))
  val eventLogId = TestEventLog.nextId()

  def iterator = iterator(1L, 0L)

  def iterator(fromLogId: Long, fromLogEntryId: Long) =
    getEntries.drop(fromLogEntryId.toInt).iterator

  def getEntries: List[EventLogEntry] =
    Await.result((logger ? GetEntries()).mapTo[List[EventLogEntry]], 5.seconds)

  def appendAsync(event: Event): Future[EventLogEntry] =
    (logger ? LogEvent(event)).mapTo[EventLogEntry]

  case class LogEvent(event: Event)
  case class GetEntries()

  class Logger extends Actor {
    var counter = 0L;
    var entries = List.empty[EventLogEntry]
    def receive = {
      case LogEvent(event) => {
        
        val entry = EventLogEntry(eventLogId, entries.size, counter, event)
        counter = counter + 1
        entries = entry :: entries
        sender ! entry
      }
      case GetEntries() => {
        sender ! entries.reverse
      }
    }
  }
}

object TestEventLog {
  var current: Long = 0L
  def apply(system: ActorSystem) = new TestEventLog(system)
  def nextId() = {
    current = current + 1
    current
  }
}
