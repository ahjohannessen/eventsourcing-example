package dev.example.eventsourcing.service

import akka.actor.ActorSystem

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._
import dev.example.eventsourcing.state.EventProjection

class InvoiceReplicator(val system: ActorSystem) extends EventProjection[Map[String, Invoice]] {
  val initialState = Map.empty[String, Invoice]

  def project = {
    case (state, e: InvoiceCreated) => state + (e.invoiceId -> Invoice.handle(e))
    case (state, e: InvoiceEvent)   => state + (e.invoiceId -> state(e.invoiceId).handle(e))
  }
}

object InvoiceReplicator {
  // TODO: refactor (introduce recoverResequenced)
  def recover(sys: ActorSystem, eventLog: EventLog, resequenced: Boolean = false): InvoiceReplicator = {
    val replicator =
      if (resequenced) new InvoiceReplicator(sys) with Resequenced
      else             new InvoiceReplicator(sys)
    replicator.recover(eventLog)
    replicator.await()
    replicator
  }
}
