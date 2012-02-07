package dev.example.eventsourcing.service

import akka.actor._
import akka.dispatch.Await
import akka.util.duration._

import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, WordSpec}
import org.scalatest.matchers.MustMatchers

import scalaz._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._

class InvoiceServiceSpec extends WordSpec with MustMatchers with BeforeAndAfterEach with BeforeAndAfterAll {
  val testSystem = ActorSystem("test")
  val eventLog = TestEventLog(testSystem)
  val service = InvoiceService(testSystem, eventLog)

  override def afterAll = testSystem.shutdown()

  "An invoice service" when {
    "asked to create a new invoice" must {
      "return the created invoice" in {
        Await.result(service.createInvoice("test"), 5.seconds) must
          be(Success(DraftInvoice("test", version = 0)))
      }
      "have the creation event logged" in {
        eventLog.toList.last.event must be(InvoiceCreated("test"))
        eventLog.toList.length must be(1)
      }
    }
    "asked to create an invoice with an existing id" must {
      "return an error" in {
        Await.result(service.createInvoice("test"), 5.seconds) must
          be(Failure(DomainError("invoice test: already exists")))
      }
    }
    "asked to update an existing invoice" must {
      "return the updated invoice" in {
        Await.result(service.addInvoiceItem("test", None, InvoiceItem("a", 0, 0)), 5.seconds) must
          be(Success(DraftInvoice(id = "test", version = 1, items = List(InvoiceItem("a", 0, 0)))))
      }
      "have the update event logged" in {
        eventLog.toList.last.event must be(InvoiceItemAdded("test", InvoiceItem("a", 0, 0)))
        eventLog.toList.length must be(2)
      }
    }
    "asked to update a non-existing invoice" must {
      "return an error" in {
        Await.result(service.addInvoiceItem("foo", None, InvoiceItem("b", 0, 0)), 5.seconds) must
          be(Failure(DomainError("invoice foo: does not exist")))
      }
      "not have the event log updated" in {
        eventLog.toList.length must be(2)
      }
    }
    "asked to update an existing invoice with matching version" must {
      "return the updated invoice" in {
        Await.result(service.addInvoiceItem("test", Some(1), InvoiceItem("b", 0, 0)), 5.seconds) must
          be(Success(DraftInvoice(id = "test", version = 2, items = List(InvoiceItem("a", 0, 0), InvoiceItem("b", 0, 0)))))
      }
      "have the update event logged" in {
        eventLog.toList.last.event must be(InvoiceItemAdded("test", InvoiceItem("b", 0, 0)))
        eventLog.toList.length must be(3)
      }
    }
    "asked to update an existing invoice with non-matching version" must {
      "return an error" in {
        Await.result(service.addInvoiceItem("test", Some(1), InvoiceItem("c", 0, 0)), 5.seconds) must
          be(Failure(DomainError("invoice test: expected version 1 doesn't match current version 2")))
      }
      "not have the event log updated" in {
        eventLog.toList.length must be(3)
      }
    }
  }
}
