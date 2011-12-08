package dev.example.eventsourcing.service

import scala.collection.immutable.Queue

import akka.actor._
import akka.dispatch._
import akka.stm._

import scalaz._
import Scalaz._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.log.EventLog

class InvoiceService(eventLog: EventLog[InvoiceEvent], initialState: Map[String, Invoice] = Map.empty) {
  import InvoiceService._

  val invoicesRef = Ref(initialState)
  val updatesRef = Ref(Queue.empty[TransientUpdate])

  val updateProcessor = Actor.actorOf(new InvoiceUpdateProcessor(invoicesRef, updatesRef, eventLog)).start

  //
  // Consistent reads
  //

  def getInvoice(invoiceId: String): Option[Invoice] = invoicesRef().get(invoiceId)
  def getInvoices: Iterable[Invoice] = invoicesRef().values
  
  //
  // Updates
  //

  def createInvoice(invoiceId: String): Future[DomainValidation[Invoice]] =
    transactedUpdate(invoiceId) { (persistentInvoiceOption, transientInvoiceOption) =>
      (persistentInvoiceOption, transientInvoiceOption) match {
        case (Some(p), _      ) => Update.reject(DomainError("invoice %s: already exists" format invoiceId))
        case (None   , Some(t)) => Update.reject(DomainError("invoice %s: concurrent creation in progress" format invoiceId))
        case (None   , None   ) => Invoice.create(invoiceId)
      }
    }

  def updateInvoice(invoiceId: String, expectedVersionOption: Option[Long])(f: Invoice => Update[InvoiceEvent, Invoice]): Future[DomainValidation[Invoice]] =
    transactedUpdate(invoiceId) { (persistentInvoiceOption, transientInvoiceOption) =>
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

  //
  // Internals
  //

  private def transactedUpdate(invoiceId: String)(f: (Option[Invoice], Option[Invoice]) => Update[InvoiceEvent, Invoice]) = atomic {
    val persistedInvoices = invoicesRef()
    val transientInvoices = updatesRef().lastOption.map(_.invoices).getOrElse(persistedInvoices)

    val future = new DefaultCompletableFuture[DomainValidation[Invoice]]

    f(persistedInvoices.get(invoiceId), transientInvoices.get(invoiceId))() match {
      case (events, Failure(errors))  => future.completeWithResult(Failure(errors))
      case (events, Success(updated)) => {
        updatesRef alter { queue => queue.enqueue(TransientUpdate(transientInvoices + (invoiceId -> updated), events)) }
        deferred {
          updateProcessor ? InvoiceUpdateProcessor.Run() onResult {
            case InvoiceUpdateProcessor.UpdateSuccess() => future.completeWithResult(Success(updated))
            case InvoiceUpdateProcessor.UpdateFailure() => () // TODO: report update/persistence error
          }
        }
      }
    }
    future
  }
}

object InvoiceService {
  case class TransientUpdate(invoices: Map[String, Invoice], events: List[InvoiceEvent])

  def apply(eventLog: EventLog[InvoiceEvent]): InvoiceService =
    new InvoiceService(eventLog)

  def apply(eventLog: EventLog[InvoiceEvent], history: List[InvoiceEvent]): InvoiceService =
    new InvoiceService(eventLog, handle(history))

  def apply(eventLog: EventLog[InvoiceEvent], initialState: Map[String, Invoice]): InvoiceService =
    new InvoiceService(eventLog, initialState)

  def handle(events: List[InvoiceEvent]) = events.foldLeft(Map.empty[String, Invoice]) { (m, e) =>
    e match {
      case InvoiceCreated(invoiceId) => m + (invoiceId -> Invoice.handle(e))
      case event                     => m + (event.invoiceId -> m(event.invoiceId).handle(e))
    }
  }
}