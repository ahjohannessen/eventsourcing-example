package dev.example.eventsourcing.event

import akka.actor._
import akka.dispatch._

trait Event

case class EventLogged(entry: EventLogEntry) extends Event
case class EventLogEntry(logId: Long, logEntryId: Long, event: Event)

trait EventLog extends Iterable[EventLogEntry] {
  def iterator: Iterator[EventLogEntry] = iterator(1L, 0L)
  def iterator(fromLogId: Long, fromLogEntryId: Long): Iterator[EventLogEntry]

  def appendAsync(event: Event): Future[EventLogEntry]
  def append(event: Event): EventLogEntry = appendAsync(event).get
}

trait EventBus {
  def subscribe(subscriber: Subscriber)
  def unsubscribe(subscriber: Subscriber)
  def publish(entry: EventLogEntry)
}

trait EventLogEntryPublication extends EventLog {
  def eventBus: EventBus

  abstract override def appendAsync(event: Event): Future[EventLogEntry] = {
    val future = super.appendAsync(event)
    future.onResult { case entry => eventBus.publish(entry) }
    future
  }
}

class EventLogEntryResequencer(target: ActorRef) extends Actor {
  import scala.collection.mutable.Map

  val delayed = Map.empty[Long, EventLogEntry]
  var delivered = -1L

  def receive = {
    case entry: EventLogEntry => resequence(entry)
  }

  private def resequence(entry: EventLogEntry) {
    if (entry.logEntryId == delivered + 1) {
      delivered = entry.logEntryId
      target forward entry
    } else {
      delayed += (entry.logEntryId -> entry)
    }
    delayed.remove(delivered + 1).foreach(resequence)
  }
}

trait Subscriber {
  def handle(entry: EventLogEntry)
}


