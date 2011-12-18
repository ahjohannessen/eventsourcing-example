package dev.example.eventsourcing.service

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event.Event
import dev.example.eventsourcing.state._

class InvoiceReplicator extends EventProjection[Map[String, Invoice]] {
  val initialState = Map.empty[String, Invoice]

  val projectionLogic = (state: Map[String, Invoice], event: Event) => event match {
    case e: InvoiceCreated => state + (e.invoiceId -> Invoice.handle(e))
    case e: InvoiceEvent   => state + (e.invoiceId -> state(e.invoiceId).handle(e))
  }
}
