package dev.example.eventsourcing.event

trait EventLog[-E <: Event] {
  def append(event: E)
}
