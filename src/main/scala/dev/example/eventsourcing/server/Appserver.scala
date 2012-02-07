package dev.example.eventsourcing.server

import akka.actor.ActorSystem

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
    val system = ActorSystem("eventsourcing")

    val eventLog = new JournalioEventLog(system) with EventLogEntryPublication { val channel = new SimpleChannel[EventLogEntry](system) }

    // read models
    val invoiceReplicator = InvoiceReplicator.recover(system, eventLog, true)
    val invoiceStatistics = InvoiceStatistics.recover(system, eventLog)

    // services
    val invoiceService = InvoiceService(system, eventLog, invoiceReplicator.currentState)
    val paymentService = PaymentService(system, eventLog)

    // processes
    val paymentProcess = PaymentProcess(paymentService, invoiceService)

    // event subscriptions
    eventLog.channel.subscribe(invoiceReplicator)
    eventLog.channel.subscribe(invoiceStatistics)
    eventLog.channel.subscribe(paymentProcess)
  }
}