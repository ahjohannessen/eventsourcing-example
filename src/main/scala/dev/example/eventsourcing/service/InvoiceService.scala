package dev.example.eventsourcing.service

import akka.stm._

import scalaz._
import Scalaz._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.log.MockEventLog

class InvoiceService(initialState: Map[String, Invoice] = Map.empty) {
  val invoicesRef = Ref(initialState)
  val eventLog = MockEventLog()

  def createInvoice(invoiceId: String): DomainValidation[Invoice] = atomic {
    deferred { eventLog.storeAsync() }
    Invoice.create(invoiceId).log(eventLog).result { created =>
      invoicesRef alter { invoices => invoices + (invoiceId -> created) }
    }
  }

  def updateInvoice(invoiceId: String)(f: Invoice => Update[Invoice]): DomainValidation[Invoice] = atomic {
    deferred { eventLog.storeAsync() }
    invoicesRef().get(invoiceId) match {
      case None          => DomainError("no invoice with id %s").fail
      case Some(invoice) => f(invoice).log(eventLog).result { updated =>
        invoicesRef alter { invoices => invoices + (invoiceId -> updated) }
      }
    }
  }

  def addInvoiceItem(invoiceId: String, invoiceItem: InvoiceItem) = updateInvoice(invoiceId) { invoice =>
    invoice.addItem(invoiceItem)
  }

  def setInvoiceDiscount(invoiceId: String, discount: BigDecimal) = updateInvoice(invoiceId) { invoice =>
    invoice.setDiscount(discount)
  }

  def sendInvoiceTo(invoiceId: String, to: InvoiceAddress) = updateInvoice(invoiceId) { invoice =>
    invoice.sendTo(to)
  }
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