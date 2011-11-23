package dev.example.eventsourcing.log

import akka.actor.Actor
import akka.stm.Ref

import dev.example.eventsourcing.domain.Event

class TestEventLog extends EventLog {
  val eventStore = Actor.actorOf(new TestEventStore(events)).start
}

object TestEventLog {
  def apply() = new TestEventLog
}

class TestEventStore(val events: Ref[List[Event]]) extends Actor with EventStore {
  import TestEventStore._

  def store(event: Event) = stored = event :: stored
}

object TestEventStore {
  var stored = List.empty[Event]

  def clear() {
    stored = Nil
  }
}
