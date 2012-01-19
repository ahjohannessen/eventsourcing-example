package dev.example.eventsourcing.server

import dev.example.eventsourcing.event._
import dev.example.eventsourcing.event.impl.JournalioEventLog
import dev.example.eventsourcing.service._

trait Appserver {
  val invoiceReplicator: InvoiceReplicator
  val invoiceStatistics: InvoiceStatistics

  val invoiceService: InvoiceService
  val paymentService: PaymentService
}

object Appserver {
  def boot(): Appserver = new Appserver {
    val eventLog = new JournalioEventLog with EventLogEntryPublication { val channel = new SimpleChannel[EventLogEntry] }

    // read models
    val invoiceReplicator = InvoiceReplicator.replay(eventLog, true)
    val invoiceStatistics = InvoiceStatistics.replay(eventLog)

    // services
    val invoiceService = InvoiceService(eventLog, invoiceReplicator.currentState)
    val paymentService = PaymentService(eventLog)

    // processes
    val paymentProcess = PaymentProcess(paymentService, invoiceService)

    // event subscriptions
    eventLog.channel.subscribe(invoiceReplicator)
    eventLog.channel.subscribe(invoiceStatistics)
    eventLog.channel.subscribe(paymentProcess)
  }
}