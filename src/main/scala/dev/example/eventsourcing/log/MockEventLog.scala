package dev.example.eventsourcing.log

import akka.actor.Actor
import akka.stm.Ref

import dev.example.eventsourcing.domain.Event

class MockEventLog extends EventLog {
  val eventStore = Actor.actorOf(new MockEventStore(events)).start
}

object MockEventLog {
  def apply() = new MockEventLog
}

class MockEventStore(val events: Ref[List[Event]]) extends Actor with EventStore {
  def store(event: Event) = println("stored %s" format event)
}
