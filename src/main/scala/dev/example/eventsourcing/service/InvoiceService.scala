package dev.example.eventsourcing.service

import akka.dispatch._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._
import dev.example.eventsourcing.state._

trait InvoiceService extends Stateful[Map[String, Invoice], InvoiceEvent, Invoice] {
  def invoices = stateRef()

  //
  // Consistent reads
  //

  def getInvoice(invoiceId: String): Option[Invoice] = invoices.get(invoiceId)
  def getInvoices: Iterable[Invoice] = invoices.values
  
  //
  // Updates
  //

  private val transition = (currentState: Map[String, Invoice], updated: Invoice) => currentState + (updated.id -> updated)

  def createInvoice(invoiceId: String) = transacted(currentState =>
    currentState.get(invoiceId) match {
      case Some(invoice) => Update.reject(DomainError("invoice %s: already exists" format invoiceId))
      case None          => Invoice.create(invoiceId)
    }, transition)


  def updateInvoice(invoiceId: String, expectedVersion: Option[Long])(f: Invoice => Update[InvoiceEvent, Invoice]) = transacted(currentState =>
    currentState.get(invoiceId) match {
      case None          => Update.reject(DomainError("invoice %s: does not exist" format invoiceId))
      case Some(invoice) => for {
          current <- invoice.require(expectedVersion)
          updated <- f(invoice)
        } yield updated
    }, transition)

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

  def apply(log: EventLog[InvoiceEvent], initial: Map[String, Invoice] = Map.empty) = new InvoiceService {
    val stateRef = Ref(initial)
    val eventLog = log
  }

  def handle(events: List[InvoiceEvent]) = events.foldLeft(Map.empty[String, Invoice]) { (m, e) =>
    e match {
      case InvoiceCreated(invoiceId) => m + (invoiceId -> Invoice.handle(e))
      case event                     => m + (event.invoiceId -> m(event.invoiceId).handle(e))
    }
  }
}