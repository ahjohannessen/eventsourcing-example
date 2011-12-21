package dev.example.eventsourcing.service

import akka.actor.Actor

import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, WordSpec}
import org.scalatest.matchers.MustMatchers

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._
import dev.example.eventsourcing.state.EventProjectionCounter

class InvoiceReplicatorSpec extends WordSpec with MustMatchers with BeforeAndAfterEach with BeforeAndAfterAll {
  val eventLog = new TestEventLog with EventLogEntryPublication { val eventBus = new DefaultEventBus }
  val service = InvoiceService(eventLog)

  import eventLog.eventBus

  override def afterAll = Actor.registry.shutdownAll()

  "An invoice replicator" when {
    "sourced from a live event stream" must {
      "replicate service state" in {
        val replicator = new InvoiceReplicator with EventProjectionCounter[Map[String, Invoice]]
        eventBus.subscribe(replicator)
        replicator.expect(3)
        service.createInvoice("test").get
        service.addInvoiceItem("test", None, InvoiceItem("a", 0, 0)).get
        service.addInvoiceItem("test", None, InvoiceItem("b", 1, 1)).get
        replicator.await().state must be(service.currentState)
      }
    }
    "sourced from a replayed event stream" must {
      "recover service state" in {
        val replicator = new InvoiceReplicator with EventProjectionCounter[Map[String, Invoice]]
        replicator.expect(3)
        replicator.replay(eventLog)
        replicator.await().state must be(service.currentState)
      }
    }
  }
}