package dev.example.eventsourcing.service

import akka.stm._

import scalaz._
import Scalaz._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.log.TestEventLog

class InvoiceService(initialState: Map[String, Invoice] = Map.empty) {
  val invoicesRef = Ref(initialState)
  val eventLog = TestEventLog()

  def createInvoice(invoiceId: String): DomainValidation[Invoice] = atomic {
    deferred { eventLog.store() }
    Invoice.create(invoiceId).log(eventLog).result { created =>
      invoicesRef alter { invoices => invoices + (invoiceId -> created) }
    }
  }

  def updateInvoice(invoiceId: String, version: Option[Long])(f: Invoice => Update[Invoice]): DomainValidation[Invoice] = atomic {
    deferred { eventLog.store() }
    invoicesRef().get(invoiceId) match {
      case None          => DomainError("no invoice with id %s").fail
      case Some(invoice) => (for {
        current <- invoice.require(version)
        updated <- f(current)
      } yield updated).log(eventLog).result { updated =>
        invoicesRef alter { invoices => invoices + (invoiceId -> updated) }
      }
    }
  }

  def addInvoiceItem(invoiceId: String, version: Option[Long], invoiceItem: InvoiceItem) =
    updateInvoice(invoiceId, version) { invoice => invoice.addItem(invoiceItem) }

  def setInvoiceDiscount(invoiceId: String, version: Option[Long], discount: BigDecimal) =
    updateInvoice(invoiceId, version) { invoice => invoice.setDiscount(discount) }

  def sendInvoiceTo(invoiceId: String, version: Option[Long], to: InvoiceAddress) =
    updateInvoice(invoiceId, version) { invoice => invoice.sendTo(to) }
}

object InvoiceService {
  def apply(): InvoiceService =
    new InvoiceService

  def apply(history: List[Event]): InvoiceService =
    new InvoiceService(replay(history))

  def apply(initialState: Map[String, Invoice]): InvoiceService =
    new InvoiceService(initialState)

  def replay(events: List[Event]) = events.foldLeft(Map.empty[String, Invoice]) { (m, e) =>
    e match {
      case InvoiceCreated(invoiceId) => m + (invoiceId -> Invoice.handle(e))
      case event: InvoiceEvent => m + (event.invoiceId -> m(event.invoiceId).handle(e))
    }
  }
}