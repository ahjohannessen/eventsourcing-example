package dev.example.eventsourcing.service

import akka.actor.Actor

import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, WordSpec}
import org.scalatest.matchers.MustMatchers

import scalaz._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._

class InvoiceServiceSpec extends WordSpec with MustMatchers with BeforeAndAfterEach with BeforeAndAfterAll {
  val eventLog = TestEventLog[InvoiceEvent]()
  val service = InvoiceService(eventLog)

  override def afterAll = Actor.registry.shutdownAll()

  "An invoice service" when {
    "asked to create a new invoice" must {
      "return the created invoice" in {
        service.createInvoice("test").get must be(Success(Invoice("test", version = 0)))
      }
      "have the creation event logged" in {
        eventLog.toList.last must be(InvoiceCreated("test"))
        eventLog.toList.length must be(1)
      }
    }
    "asked to create an invoice with an existing id" must {
      "return an error" in {
        service.createInvoice("test").get must be(Failure(DomainError("invoice test: already exists")))
      }
    }
    "asked to update an existing invoice" must {
      "return the updated invoice" in {
        service.addInvoiceItem("test", None, InvoiceItem("a", 0, 0)).get must
          be(Success(Invoice(id = "test", version = 1, items = List(InvoiceItem("a", 0, 0)))))
      }
      "have the update event logged" in {
        eventLog.toList.last must be(InvoiceItemAdded("test", InvoiceItem("a", 0, 0)))
        eventLog.toList.length must be(2)
      }
    }
    "asked to update a non-existing invoice" must {
      "return an error" in {
        service.addInvoiceItem("foo", None, InvoiceItem("b", 0, 0)).get must be
          be(Failure(DomainError("no invoice with id foo")))
      }
      "not have the event log updated" in {
        eventLog.toList.length must be(2)
      }
    }
    "asked to update an existing invoice with matching version" must {
      "return the updated invoice" in {
        service.addInvoiceItem("test", Some(1), InvoiceItem("b", 0, 0)).get must
          be(Success(Invoice(id = "test", version = 2, items = List(InvoiceItem("b", 0, 0), InvoiceItem("a", 0, 0)))))
      }
      "have the update event logged" in {
        eventLog.toList.last must be(InvoiceItemAdded("test", InvoiceItem("b", 0, 0)))
        eventLog.toList.length must be(3)
      }
    }
    "asked to update an existing invoice with non-matching version" must {
      "return an error" in {
        service.addInvoiceItem("test", Some(1), InvoiceItem("c", 0, 0)).get must
          be(Failure(DomainError("invoice test: expected version 1 doesn't match current version 2")))
      }
      "not have the event log updated" in {
        eventLog.toList.length must be(3)
      }
    }
    "created with an event stream" must {
      "have an initial state derived from that event stream" in {
        val recovered = InvoiceService(eventLog, eventLog.toIterator)
        recovered.invoices must be(Map("test" -> Invoice("test", 2, List(InvoiceItem("b", 0, 0), InvoiceItem("a", 0, 0)))))
      }
    }
  }
}
