package dev.example.eventsourcing.event

import akka.dispatch._
import akka.util.duration._

trait Event

case class EventLogEntry(logId: Long, logEntryId: Long, seqnr: Long, event: Event)

trait EventLog extends Iterable[EventLogEntry] {
  def iterator: Iterator[EventLogEntry]
  def iterator(fromLogId: Long, fromLogEntryId: Long): Iterator[EventLogEntry]

  def appendAsync(event: Event): Future[EventLogEntry]
  def append(event: Event): EventLogEntry = Await.result(appendAsync(event), 5.seconds) // TODO: make configurable
}

trait EventLogEntryPublication extends EventLog {
  def channel: Channel[EventLogEntry]

  abstract override def appendAsync(event: Event): Future[EventLogEntry] = {
    val future = super.appendAsync(event)
    future.onSuccess { case entry => channel.publish(entry) }
    future
  }
}
