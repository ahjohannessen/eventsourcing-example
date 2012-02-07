package dev.example.eventsourcing.service

import akka.actor.ActorSystem
import akka.dispatch.Await
import akka.util.duration._

import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, WordSpec}
import org.scalatest.matchers.MustMatchers

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._
import dev.example.eventsourcing.state.EventProjectionCounter

class InvoiceReplicatorSpec extends WordSpec with MustMatchers with BeforeAndAfterEach with BeforeAndAfterAll {
  val testSystem = ActorSystem("test")

  val eventLog = new TestEventLog(testSystem) with EventLogEntryPublication { val channel = new SimpleChannel[EventLogEntry](testSystem) }
  val service = InvoiceService(testSystem, eventLog)

  import eventLog.channel

  override def afterAll = testSystem.shutdown()

  "An invoice replicator" when {
    "sourced from a live event stream" must {
      "replicate service state" in {
        val replicator = new InvoiceReplicator(testSystem) with Resequenced with EventProjectionCounter[Map[String, Invoice]]
        channel.subscribe(replicator)
        replicator.expect(3)
        Await.result(service.createInvoice("test"), 5.seconds)
        Await.result(service.addInvoiceItem("test", None, InvoiceItem("a", 0, 0)), 5.seconds)
        Await.result(service.addInvoiceItem("test", None, InvoiceItem("b", 1, 1)), 5.seconds)
        replicator.await().state must be(service.currentState)
      }
    }
    "sourced from a replayed event stream" must {
      "recover service state" in {
        val replicator = new InvoiceReplicator(testSystem) with Resequenced with EventProjectionCounter[Map[String, Invoice]]
        replicator.expect(3)
        replicator.recover(eventLog)
        replicator.await().state must be(service.currentState)
      }
    }
  }
}