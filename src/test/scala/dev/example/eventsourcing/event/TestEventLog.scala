package dev.example.eventsourcing.event

import java.util.concurrent.CopyOnWriteArrayList

class TestEventLog[E <: Event] extends EventLog[E] with Iterable[E] {
  import scala.collection.JavaConverters._

  val storedEvents = new CopyOnWriteArrayList[E]()

  def iterator = storedEvents.asScala.toList.iterator

  def append(event: E): Long = {
    storedEvents.add(event)
    storedEvents.size
  }

  def appendAsync(event: E)(f: Long => Unit) {
    f(append(event))
  }
}

object TestEventLog {
  def apply[E <: Event]() = new TestEventLog[E]
}
