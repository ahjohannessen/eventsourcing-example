package dev.example.eventsourcing.service

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._
import dev.example.eventsourcing.state.EventProjection

class InvoiceStatistics extends EventProjection[Map[String, Int]] {
  val initialState = Map.empty[String, Int]

  def projectionLogic = {
    case (state, e: InvoiceItemAdded) => state.get(e.invoiceId) match {
      case Some(count) => state + (e.invoiceId -> (count + 1))
      case None        => state + (e.invoiceId -> 1)
    }
  }
}

object InvoiceStatistics {
  def replay(eventLog: EventLog): InvoiceStatistics = {
    val statistics = new InvoiceStatistics
    statistics.replay(eventLog)
    statistics.await()
    statistics
  }
}