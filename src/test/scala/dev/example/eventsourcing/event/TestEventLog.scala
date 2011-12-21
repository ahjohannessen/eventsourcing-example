package dev.example.eventsourcing.event

import akka.actor._
import akka.dispatch._

class TestEventLog extends EventLog {
  val logger = Actor.actorOf(new Logger).start
  val eventLogId = TestEventLog.nextId()

  def iterator(fromLogId: Long, fromLogEntryId: Long) =
    getEntries.drop(fromLogEntryId.toInt).iterator

  def getEntries: List[EventLogEntry] =
    (logger ? GetEntries()).as[List[EventLogEntry]].get

  def appendAsync(event: Event): Future[EventLogEntry] =
    (logger ? LogEvent(event)).asInstanceOf[Future[EventLogEntry]]

  case class LogEvent(event: Event)
  case class GetEntries()

  class Logger extends Actor {
    var entries = List.empty[EventLogEntry]
    def receive = {
      case LogEvent(event) => {
        val entry = EventLogEntry(eventLogId, entries.size, event)
        entries = entry :: entries
        self.reply(entry)
      }
      case GetEntries() => {
        self.reply(entries.reverse)
      }
    }
  }
}

object TestEventLog {
  var current: Long = 0L
  def apply() = new TestEventLog
  def nextId() = {
    current = current + 1
    current
  }
}
