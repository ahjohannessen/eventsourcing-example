package dev.example.eventsourcing.log

import dev.example.eventsourcing.domain.Event

trait EventLog[-E <: Event] {
  def append(event: E)
}
