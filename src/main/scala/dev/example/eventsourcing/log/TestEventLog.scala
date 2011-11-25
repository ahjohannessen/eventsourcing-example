package dev.example.eventsourcing.log

import java.util.concurrent.CopyOnWriteArrayList

import akka.actor.Actor
import akka.stm.Ref

import dev.example.eventsourcing.domain.Event

class TestEventLog[E <: Event] extends EventLog[E] {
  val storedEvents = new CopyOnWriteArrayList[E]()
  val eventStore = Actor.actorOf(new TestEventStore(events, storedEvents)).start

  import scala.collection.JavaConverters._

  def stored: List[E] = storedEvents.asScala.toList
}

object TestEventLog {
  def apply[E <: Event]() = new TestEventLog[E]
}

class TestEventStore[E <: Event](val events: Ref[List[E]], stored: CopyOnWriteArrayList[E]) extends Actor with EventStore[E] {
  def store(event: E) = stored.add(event)
}
