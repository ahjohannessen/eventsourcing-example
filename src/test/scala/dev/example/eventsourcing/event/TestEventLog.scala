package dev.example.eventsourcing.event

import akka.actor._

import java.util.concurrent.CopyOnWriteArrayList

class TestEventLog extends EventLog {
  import scala.collection.JavaConverters._

  val eventLogId = TestEventLog.nextId()
  val storedEvents = new CopyOnWriteArrayList[EventLogEntry]()

  val logger = Actor.actorOf(new Logger).start

  def iterator(fromLogId: Long, fromLogEntryId: Long) =
    storedEvents.asScala.drop(fromLogEntryId.toInt).iterator

  def appendAsync(event: Event)(f: EventLogEntry => Unit) =
    logger ! LogEvent(event, f)

  case class LogEvent(event: Event, callback: EventLogEntry => Unit)

  class Logger extends Actor {
    def receive = {
      case LogEvent(event, callback) => {
        val entry = EventLogEntry(eventLogId, storedEvents.size, event)
        storedEvents.add(entry)
        callback(entry)
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
