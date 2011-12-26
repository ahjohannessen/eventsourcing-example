package dev.example.eventsourcing.service

import akka.dispatch._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._
import dev.example.eventsourcing.state._

trait InvoiceService extends UpdateProjection[Map[String, Invoice], Invoice] {
  import InvoiceService._

  //
  // Consistent reads
  //

  def getInvoice(invoiceId: String): Option[Invoice] = currentState.get(invoiceId)
  def getInvoices: Iterable[Invoice] = currentState.values

  def getDraftInvoices = getInvoices.filter(_.isInstanceOf[DraftInvoice])
  def getSentInvoices = getInvoices.filter(_.isInstanceOf[SentInvoice])
  def getPaidInvoices = getInvoices.filter(_.isInstanceOf[SentInvoice])

  //
  // Updates
  //

  def createInvoice(invoiceId: String): Future[DomainValidation[DraftInvoice]] = transacted { state =>
    state.get(invoiceId) match {
      case Some(invoice) => Update.reject(DomainError("invoice %s: already exists" format invoiceId))
      case None          => Invoice.create(invoiceId)
    }
  }

  def updateInvoice[B <: Invoice](invoiceId: String, expectedVersion: Option[Long])(f: Invoice => Update[InvoiceEvent, B]) = transacted { state =>
    state.get(invoiceId) match {
      case None          => Update.reject(DomainError("invoice %s: does not exist" format invoiceId))
      case Some(invoice) => for {
        current <- invoice.require(expectedVersion)
        updated <- f(invoice)
      } yield updated
    }
  }

  def updateDraftInvoice[B <: Invoice](invoiceId: String, expectedVersion: Option[Long])(f: DraftInvoice => Update[InvoiceEvent, B]) =
    updateInvoice(invoiceId, expectedVersion) { invoice =>
      invoice match {
        case invoice: DraftInvoice => f(invoice)
        case invoice: Invoice      => Update.reject(notDraftError(invoiceId))
      }
    }

  def addInvoiceItem(invoiceId: String, expectedVersion: Option[Long], invoiceItem: InvoiceItem): Future[DomainValidation[DraftInvoice]] =
    updateDraftInvoice(invoiceId, expectedVersion) { invoice => invoice.addItem(invoiceItem) }

  def setInvoiceDiscount(invoiceId: String, expectedVersion: Option[Long], discount: BigDecimal): Future[DomainValidation[DraftInvoice]] =
    updateDraftInvoice(invoiceId, expectedVersion) { invoice => invoice.setDiscount(discount) }

  def sendInvoiceTo(invoiceId: String, expectedVersion: Option[Long], to: InvoiceAddress): Future[DomainValidation[SentInvoice]] =
    updateDraftInvoice(invoiceId, expectedVersion) { invoice => invoice.sendTo(to) }

  def payInvoice(invoiceId: String, expectedVersion: Option[Long], amount: BigDecimal): Future[DomainValidation[PaidInvoice]] =
    updateInvoice(invoiceId, expectedVersion) { invoice =>
      invoice match {
        case invoice: SentInvoice => invoice.pay(amount)
        case invoice: Invoice      => Update.reject(notSentError(invoiceId))
      }
    }

  //
  // Projection
  //

  def projectionLogic = {
    case (state, updated) => state + (updated.id -> updated)
  }
}

object InvoiceService {
  private[service] def notDraftError(invoiceId: String) =
    DomainError("invoice %s: not a draft invoice" format invoiceId)

  private[service] def notSentError(invoiceId: String) =
    DomainError("invoice %s: not a sent invoice" format invoiceId)

  def apply(log: EventLog, initial: Map[String, Invoice] = Map.empty) = new InvoiceService {
    val eventLog = log
    val initialState = initial
  }
}