package dev.example.eventsourcing.service

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._

trait PaymentProcess extends ChannelSubscriber[EventLogEntry] {
  // TODO: failure recovery for payment process (e.g. after a node crash)
  // 
  // - store id of last processed event (e.g. using EventProjection and a storage mechanism)
  // - execute process state changes and paymentService/invoiceService updates within same tx
  // - replay events for payment process on appserver boot (beginning after last stored event)
  // - requires a transactional paymentService (invoiceService is transactional)

  def paymentService: PaymentService
  def invoiceService: InvoiceService

  def receive(message: EventLogEntry) = handle.orElse(ignore).apply(message.event)

  def handle: PartialFunction[Event, Unit] = {
    case InvoiceSent(invoiceId, invoice, to) => paymentService.requestPayment(invoice, to)
    case PaymentReceived(invoiceId, amount)  => invoiceService.payInvoice(invoiceId, None, amount)
  }
}

object PaymentProcess {
  def apply(ps: PaymentService, is: InvoiceService) = new PaymentProcess {
    val paymentService = ps
    val invoiceService = is
  }
}