package dev.example.eventsourcing.service

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._

trait PaymentProcess extends ChannelSubscriber[EventLogEntry] {
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