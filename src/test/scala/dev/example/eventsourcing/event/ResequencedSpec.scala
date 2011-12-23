package dev.example.eventsourcing.event

import java.util.concurrent.{CountDownLatch, CopyOnWriteArrayList}

import akka.actor.Actor

import org.scalatest.{BeforeAndAfterAll, WordSpec}
import org.scalatest.matchers.MustMatchers

class ResequencedSpec extends WordSpec with MustMatchers with BeforeAndAfterAll {
  override def afterAll = Actor.registry.shutdownAll()

  "A Resequenced instance" must {
    "resequence event log entries" in {
      val receiver = new Receiver(10) with Resequenced

      receiver.receive(EventLogEntry(-1, 1, 1, null))
      receiver.receive(EventLogEntry(-1, 4, 4, null))
      receiver.receive(EventLogEntry(-1, 0, 0, null))
      receiver.receive(EventLogEntry(-1, 2, 2, null))
      receiver.receive(EventLogEntry(-1, 8, 8, null))
      receiver.receive(EventLogEntry(-1, 3, 3, null))
      receiver.receive(EventLogEntry(-1, 7, 7, null))
      receiver.receive(EventLogEntry(-1, 6, 6, null))
      receiver.receive(EventLogEntry(-1, 9, 9, null))
      receiver.receive(EventLogEntry(-1, 5, 5, null))

      receiver.messages.map(_.logEntryId) must be(List(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))
    }
  }
}

class Receiver(num: Int) extends ChannelSubscriber[EventLogEntry] {
  val expected = new CountDownLatch(10)
  var received = List.empty[EventLogEntry]

  def receive(message: EventLogEntry) = {
    received = message :: received
    expected.countDown()
  }

  def messages = {
    expected.await()
    received.reverse
  }
}