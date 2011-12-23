package dev.example.eventsourcing.service

import akka.dispatch._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._
import dev.example.eventsourcing.state._

trait InvoiceService extends UpdateProjection[Map[String, Invoice], Invoice] {
  val projectionLogic = (state: Map[String, Invoice], updated: Invoice) => state + (updated.id -> updated)

  //
  // Consistent reads
  //

  def getInvoice(invoiceId: String): Option[Invoice] = currentState.get(invoiceId)
  def getInvoices: Iterable[Invoice] = currentState.values
  
  //
  // Updates
  //

  def createInvoice(invoiceId: String) = transacted { state =>
    state.get(invoiceId) match {
      case Some(invoice) => Update.reject(DomainError("invoice %s: already exists" format invoiceId))
      case None          => Invoice.create(invoiceId)
    }
  }

  def updateInvoice(invoiceId: String, expectedVersion: Option[Long])(f: Invoice => Update[InvoiceEvent, Invoice]) = transacted { state =>
    state.get(invoiceId) match {
      case None          => Update.reject(DomainError("invoice %s: does not exist" format invoiceId))
      case Some(invoice) => for {
        current <- invoice.require(expectedVersion)
        updated <- f(invoice)
      } yield updated
    }
  }

  def addInvoiceItem(invoiceId: String, version: Option[Long], invoiceItem: InvoiceItem): Future[DomainValidation[Invoice]] =
    updateInvoice(invoiceId, version) { invoice => invoice.addItem(invoiceItem) }

  def setInvoiceDiscount(invoiceId: String, version: Option[Long], discount: BigDecimal): Future[DomainValidation[Invoice]] =
    updateInvoice(invoiceId, version) { invoice => invoice.setDiscount(discount) }

  def sendInvoiceTo(invoiceId: String, version: Option[Long], to: InvoiceAddress): Future[DomainValidation[Invoice]] =
    updateInvoice(invoiceId, version) { invoice => invoice.sendTo(to) }

  def payInvoice(invoiceId: String, version: Option[Long], amount: BigDecimal): Future[DomainValidation[Invoice]] =
    updateInvoice(invoiceId, version) { invoice => invoice.pay(amount) }
}

object InvoiceService {
  def apply(log: EventLog, initial: Map[String, Invoice] = Map.empty) = new InvoiceService {
    val eventLog = log
    val initialState = initial
  }
}