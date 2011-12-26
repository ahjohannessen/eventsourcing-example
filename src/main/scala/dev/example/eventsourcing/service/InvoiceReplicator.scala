package dev.example.eventsourcing.service

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._
import dev.example.eventsourcing.state.EventProjection

class InvoiceReplicator extends EventProjection[Map[String, Invoice]] {
  val initialState = Map.empty[String, Invoice]

  def projectionLogic = {
    case (state, e: InvoiceCreated) => state + (e.invoiceId -> Invoice.handle(e))
    case (state, e: InvoiceEvent)   => state + (e.invoiceId -> state(e.invoiceId).handle(e))
  }
}

object InvoiceReplicator {
  def replay(eventLog: EventLog, resequenced: Boolean = false): InvoiceReplicator = {
    val replicator =
      if (resequenced) new InvoiceReplicator with Resequenced
      else             new InvoiceReplicator
    replicator.replay(eventLog)
    replicator.await()
    replicator
  }
}
