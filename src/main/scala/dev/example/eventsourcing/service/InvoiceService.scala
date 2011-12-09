package dev.example.eventsourcing.service

import akka.dispatch._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._

trait InvoiceService extends Transacted[InvoiceEvent, Invoice] {

  //
  // Consistent reads
  //

  def getInvoice(invoiceId: String): Option[Invoice] = domainObjectsRef().get(invoiceId)
  def getInvoices: Iterable[Invoice] = domainObjectsRef().values
  
  //
  // Updates
  //

  def createInvoice(invoiceId: String): Future[DomainValidation[Invoice]] =
    update(invoiceId) { (persistentInvoiceOption, transientInvoiceOption) =>
      (persistentInvoiceOption, transientInvoiceOption) match {
        case (Some(p), _      ) => Update.reject(DomainError("invoice %s: already exists" format invoiceId))
        case (None   , Some(t)) => Update.reject(DomainError("invoice %s: concurrent creation in progress" format invoiceId))
        case (None   , None   ) => Invoice.create(invoiceId)
      }
    }

  def updateInvoice(invoiceId: String, expectedVersionOption: Option[Long])(f: Invoice => Update[InvoiceEvent, Invoice]): Future[DomainValidation[Invoice]] =
    update(invoiceId) { (persistentInvoiceOption, transientInvoiceOption) =>
      (persistentInvoiceOption, transientInvoiceOption) match {
        case (None   , _      ) => Update.reject(DomainError("invoice %s: does not exist" format invoiceId))
        case (Some(p), None   ) => Update.reject(DomainError("invoice %s: concurrent deletion in progress" format invoiceId))
        case (Some(p), Some(t)) => for {
          current <- t.require(expectedVersionOption, p.version)
          updated <- f(t)
        } yield updated
      }
    }

  def addInvoiceItem(invoiceId: String, version: Option[Long], invoiceItem: InvoiceItem): Future[DomainValidation[Invoice]] =
    updateInvoice(invoiceId, version) { invoice => invoice.addItem(invoiceItem) }

  def setInvoiceDiscount(invoiceId: String, version: Option[Long], discount: BigDecimal): Future[DomainValidation[Invoice]] =
    updateInvoice(invoiceId, version) { invoice => invoice.setDiscount(discount) }

  def sendInvoiceTo(invoiceId: String, version: Option[Long], to: InvoiceAddress): Future[DomainValidation[Invoice]] =
    updateInvoice(invoiceId, version) { invoice => invoice.sendTo(to) }
}

object InvoiceService {
  import akka.stm._

  def apply(eventLog: EventLog[InvoiceEvent], history: List[InvoiceEvent]): InvoiceService =
    InvoiceService(eventLog, handle(history))

  def apply(log: EventLog[InvoiceEvent], invoices: Map[String, Invoice] = Map.empty) = new InvoiceService {
    val domainObjectsRef = Ref(invoices)
    val eventLog = log
  }

  def handle(events: List[InvoiceEvent]) = events.foldLeft(Map.empty[String, Invoice]) { (m, e) =>
    e match {
      case InvoiceCreated(invoiceId) => m + (invoiceId -> Invoice.handle(e))
      case event                     => m + (event.invoiceId -> m(event.invoiceId).handle(e))
    }
  }
}