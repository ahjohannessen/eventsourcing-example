package dev.example.eventsourcing.service

import akka.actor.ActorSystem

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._
import dev.example.eventsourcing.state.EventProjection

class InvoiceStatistics(val system: ActorSystem) extends EventProjection[Map[String, Int]] {
  val initialState = Map.empty[String, Int]

  def project = {
    case (state, e: InvoiceItemAdded) => state.get(e.invoiceId) match {
      case Some(count) => state + (e.invoiceId -> (count + 1))
      case None        => state + (e.invoiceId -> 1)
    }
  }
}

object InvoiceStatistics {
  def recover(sys: ActorSystem, eventLog: EventLog): InvoiceStatistics = {
    val statistics = new InvoiceStatistics(sys)
    statistics.recover(eventLog)
    statistics.await()
    statistics
  }
}