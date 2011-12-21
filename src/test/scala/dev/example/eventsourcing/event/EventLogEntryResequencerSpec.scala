package dev.example.eventsourcing.event

import scala.collection.mutable.Buffer

import akka.actor.Actor

import org.scalatest.{BeforeAndAfterAll, WordSpec}
import org.scalatest.matchers.MustMatchers

class EventLogEntryResequencerSpec extends WordSpec with MustMatchers with BeforeAndAfterAll {
  override def afterAll = Actor.registry.shutdownAll()

  "A resequencer" must {
    "resequence event log entries" in {
      val target = Actor.actorOf(new EventLogEntryResequencerTarget(10)).start
      val resequencer = Actor.actorOf(new EventLogEntryResequencer(target)).start

      resequencer ! EventLogEntry(-1, 1, null)
      resequencer ! EventLogEntry(-1, 4, null)
      resequencer ! EventLogEntry(-1, 0, null)
      resequencer ! EventLogEntry(-1, 2, null)
      resequencer ! EventLogEntry(-1, 8, null)
      resequencer ! EventLogEntry(-1, 3, null)
      resequencer ! EventLogEntry(-1, 7, null)
      resequencer ! EventLogEntry(-1, 6, null)
      resequencer ! EventLogEntry(-1, 9, null)

      (resequencer ? EventLogEntry(-1, 5, null)).as[List[EventLogEntry]] match {
        case Some(result) => result.map(entry => entry.logEntryId) must be(List(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))
        case None         => fail("no result")
      }
    }
  }
}

class EventLogEntryResequencerTarget(max: Int) extends Actor {
  val received = Buffer.empty[EventLogEntry]

  protected def receive = {
    case event: EventLogEntry => {
      received += event
      if (received.size == max)
        self.reply(received.toList)
    }
  }
}