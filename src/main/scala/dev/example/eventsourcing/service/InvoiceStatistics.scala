package dev.example.eventsourcing.service

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event.Event
import dev.example.eventsourcing.state.EventProjection

class InvoiceStatistics extends EventProjection[Map[String, Int]] {
  val initialState = Map.empty[String, Int]

  val projectionLogic = (state: Map[String, Int], event: Event) => {
    def increment(invoiceId: String) = state.get(invoiceId) match {
      case Some(count) => state + (invoiceId -> (count + 1))
      case None        => state + (invoiceId -> 1)
    }
    event match {
      case e: InvoiceItemAdded => increment(e.invoiceId)
      case e: InvoiceEvent     => state
    }
  }
}
