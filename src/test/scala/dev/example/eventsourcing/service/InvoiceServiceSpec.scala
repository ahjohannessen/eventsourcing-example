package dev.example.eventsourcing.service

import akka.actor.Actor

import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, WordSpec}
import org.scalatest.matchers.MustMatchers

import scalaz._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.log.TestEventStore

class InvoiceServiceSpec extends WordSpec with MustMatchers with BeforeAndAfterEach with BeforeAndAfterAll {
  val service = InvoiceService()

  override def afterAll {
    Actor.registry.shutdownAll()
    TestEventStore.clear()
  }

  "An invoice service" when {
    "asked to create a new invoice" must {
      "return the created invoice" in {
        service.createInvoice("test") must be(Success(Invoice("test", version = 0)))
      }
      "have the creation event logged" in {
        TestEventStore.stored(0) must be(InvoiceCreated("test"))
        TestEventStore.stored.length must be(1)
      }
    }
    "asked to update an existing invoice" must {
      "return the updated invoice" in {
        service.addInvoiceItem("test", None, InvoiceItem("a", 0, 0)) must
          be(Success(Invoice(id = "test", version = 1, items = List(InvoiceItem("a", 0, 0)))))
      }
      "have the update event logged" in {
        TestEventStore.stored(0) must be(InvoiceItemAdded("test", InvoiceItem("a", 0, 0)))
        TestEventStore.stored.length must be(2)
      }
    }
    "asked to update a non- existing invoice" must {
      "return an error" in {
        service.addInvoiceItem("foo", None, InvoiceItem("b", 0, 0)) must be
          be(Failure(DomainError("no invoice with id foo")))
      }
      "not have the event log updated" in {
        TestEventStore.stored.length must be(2)
      }
    }
    "asked to update an existing invoice with matching version" must {
      "return the updated invoice" in {
        service.addInvoiceItem("test", Some(1), InvoiceItem("b", 0, 0)) must
          be(Success(Invoice(id = "test", version = 2, items = List(InvoiceItem("b", 0, 0), InvoiceItem("a", 0, 0)))))
      }
      "have the update event logged" in {
        TestEventStore.stored(0) must be(InvoiceItemAdded("test", InvoiceItem("b", 0, 0)))
        TestEventStore.stored.length must be(3)
      }
    }
    "asked to update an existing invoice with non-matching version" must {
      "return an error" in {
        service.addInvoiceItem("test", Some(1), InvoiceItem("c", 0, 0)) must
          be(Failure(DomainError("expected version = 1, actual version = 2")))
      }
      "not have the event log updated" in {
        TestEventStore.stored.length must be(3)
      }
    }
  }
}
