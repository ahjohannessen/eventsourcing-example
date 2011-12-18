package dev.example.eventsourcing.event

import akka.actor.ActorRef

trait Event

case class EventLogged(eventLogEntry: EventLogEntry) extends Event
case class EventLogEntry(logId: Long, logEntryId: Long, event: Event)

trait EventLog extends Iterable[EventLogEntry] {
  def iterator: Iterator[EventLogEntry] = iterator(1L, 0L)
  def iterator(fromLogId: Long, fromLogEntryId: Long): Iterator[EventLogEntry]

  def append(event: Event): EventLogEntry
  def appendAsync(event: Event)(f: EventLogEntry => Unit)
}

trait EventBus {
  def subscribe(subscriber: Subscriber, topic: String)
  def unsubscribe(subscriber: Subscriber, topic: String)
  def publish(event: Event, topic: String)
}

trait EventLoggedNotification extends EventLog {
  def eventLogId: Long
  def eventBus: EventBus

  abstract override def append(event: Event) = {
    val eventLogEntry = super.append(event)
    val eventLogged = EventLogged(eventLogEntry)
    eventBus.publish(eventLogged, "log")
    eventLogEntry
  }
}

trait Subscriber {
  def onEvent(event: Event)
}


