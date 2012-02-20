package dev.example.eventsourcing.service

import akka.actor.ActorSystem

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event.EventLog

/**
 * Mock payment service.
 */
trait PaymentService {
  def eventLog: EventLog

  def requestPayment(invoice: Invoice, to: InvoiceAddress) {
    // send an invoice to invoice address and
    // after having received the payment ...
    eventLog.append(PaymentReceived(invoice.id, invoice.total))
  }
}

object PaymentService {
  def apply(sys: ActorSystem, log: EventLog) = new PaymentService {
    val eventLog = log
  }
}