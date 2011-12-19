package dev.example.eventsourcing.event

import java.util.concurrent.CopyOnWriteArrayList

class TestEventLog extends EventLog {
  import scala.collection.JavaConverters._

  val eventLogId = TestEventLog.nextId
  val storedEvents = new CopyOnWriteArrayList[EventLogEntry]()

  def iterator(fromLogId: Long, fromLogEntryId: Long) =
    storedEvents.asScala.drop(fromLogEntryId.toInt).iterator

  def append(event: Event): EventLogEntry = {
    val entry = EventLogEntry(eventLogId, storedEvents.size, event)
    storedEvents.add(entry)
    entry
  }

  def appendAsync(event: Event)(f: EventLogEntry => Unit) {
    f(append(event))
  }
}

object TestEventLog {
  var current: Long = 0L
  def apply() = new TestEventLog
  def nextId = {
    current = current + 1
    current
  }
}
