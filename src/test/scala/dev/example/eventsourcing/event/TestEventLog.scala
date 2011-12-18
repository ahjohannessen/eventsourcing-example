package dev.example.eventsourcing.event

import java.util.concurrent.CopyOnWriteArrayList

class TestEventLog extends EventLog {
  import scala.collection.JavaConverters._

  val eventLogId = 1L
  val storedEvents = new CopyOnWriteArrayList[EventLogEntry]()

  def iterator(fromLogId: Long, fromLogEntryId: Long) =
    storedEvents.asScala.drop(fromLogEntryId.toInt).iterator

  def append(event: Event): EventLogEntry = {
    val entry = EventLogEntry(1L, storedEvents.size, event)
    storedEvents.add(entry)
    entry
  }

  def appendAsync(event: Event)(f: EventLogEntry => Unit) {
    f(append(event))
  }
}

object TestEventLog {
  def apply() = new TestEventLog
}
