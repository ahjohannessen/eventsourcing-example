package dev.example.eventsourcing.service

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._
import dev.example.eventsourcing.state.EventProjection

class InvoiceReplicator extends EventProjection[Map[String, Invoice]] {
  val initialState = Map.empty[String, Invoice]

  val projectionLogic = (state: Map[String, Invoice], event: Event) => event match {
    case e: InvoiceCreated => state + (e.invoiceId -> Invoice.handle(e))
    case e: InvoiceEvent   => state + (e.invoiceId -> state(e.invoiceId).handle(e))
  }
}

object InvoiceReplicator {
  def replay(eventLog: EventLog): InvoiceReplicator = {
    val replicator = new InvoiceReplicator
    replicator.replay(eventLog)
    replicator.await()
    replicator
  }
}
