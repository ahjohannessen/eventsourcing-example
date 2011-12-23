package dev.example.eventsourcing.process

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._
import dev.example.eventsourcing.service._

trait PaymentProcess extends ChannelSubscriber[EventLogEntry] {
  def paymentService: PaymentService
  def invoiceService: InvoiceService

  // A process (saga):
  // - receives events
  // - issues commands

  def receive(message: EventLogEntry) = message.event match {
    case InvoiceSent(invoiceId, invoice, to) => {
        paymentService.requestPayment(invoice, to)
    }
    case PaymentReceived(invoiceId, amount) => {
      invoiceService.payInvoice(invoiceId, None, amount)
    }
  }
}

object PaymentProcess {
  def apply(ps: PaymentService, is: InvoiceService) = new PaymentProcess {
    val paymentService = ps
    val invoiceService = is
  }
}