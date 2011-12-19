package dev.example.eventsourcing.event

import java.util.concurrent.Exchanger

trait Event

case class EventLogged(eventLogEntry: EventLogEntry) extends Event
case class EventLogEntry(logId: Long, logEntryId: Long, event: Event)

trait EventLog extends Iterable[EventLogEntry] {
  def iterator: Iterator[EventLogEntry] = iterator(1L, 0L)
  def iterator(fromLogId: Long, fromLogEntryId: Long): Iterator[EventLogEntry]

  def appendAsync(event: Event)(f: EventLogEntry => Unit)

  def append(event: Event): EventLogEntry = {
    val exchanger = new Exchanger[EventLogEntry]()
    appendAsync(event: Event) { entry => exchanger.exchange(entry) }
    exchanger.exchange(null)
  }
}

trait EventBus {
  def subscribe(subscriber: Subscriber, topic: String)
  def unsubscribe(subscriber: Subscriber, topic: String)
  def publish(event: Event, topic: String)
}

trait EventLoggedNotification extends EventLog {
  def eventLogId: Long
  def eventBus: EventBus

  abstract override def appendAsync(event: Event)(f: EventLogEntry => Unit) {
    super.appendAsync(event) { entry =>
      f(entry)
      eventBus.publish(EventLogged(entry), "log")
    }
  }
}

trait Subscriber {
  def onEvent(event: Event)
}


