package dev.example.eventsourcing.server

import dev.example.eventsourcing.event._
import dev.example.eventsourcing.event.impl.JournalioEventLog
import dev.example.eventsourcing.service._

trait Appserver {
  val invoiceReplicator: InvoiceReplicator
  val invoiceStatistics: InvoiceStatistics
  val invoiceService: InvoiceService
}

object Appserver {
  def boot(): Appserver = new Appserver {
    val eventLog = new JournalioEventLog with EventLogEntryPublication { val channel = new Channel[EventLogEntry] }

    val invoiceReplicator = InvoiceReplicator.replay(eventLog)
    val invoiceStatistics = InvoiceStatistics.replay(eventLog)
    val invoiceService = InvoiceService(eventLog, invoiceReplicator.currentState)

    eventLog.channel.subscribe(invoiceReplicator)
    eventLog.channel.subscribe(invoiceStatistics)
  }
}