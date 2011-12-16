package dev.example.eventsourcing.event

trait EventLoggedNotification[-E <: Event] extends EventLog[E] {
  def eventLogId: Long
  def eventBus: EventBus

  abstract override def append(event: E) = {
    val eventLogEntryId = super.append(event)
    val eventLogged = EventLogged(eventLogId, eventLogEntryId, event)
    eventBus.publish(eventLogged, "log")
    eventLogEntryId
  }
}

case class EventLogged(logId: Long, logEntryId: Long, loggedEvent: Event) extends Event