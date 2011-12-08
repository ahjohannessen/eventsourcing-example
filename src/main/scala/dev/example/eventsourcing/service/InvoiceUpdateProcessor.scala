package dev.example.eventsourcing.service

import scala.collection.immutable.Queue

import akka.actor._
import akka.stm._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.log.EventLog
import dev.example.eventsourcing.service.InvoiceService._

class InvoiceUpdateProcessor(
    invoicesRef: Ref[Map[String, Invoice]],
    updatesRef: Ref[Queue[TransientUpdate]],
    eventLog: EventLog[InvoiceEvent]) extends Actor {

  import InvoiceUpdateProcessor._

  protected def receive = {
    case Run() => {
      // dequeue updated transient invoices
      val transientUpdate = atomic {
        val (u, q) = updatesRef().dequeue
        updatesRef set q
        u
      }

      // persist events // TODO: failure recovery
      transientUpdate.events.reverse.foreach(eventLog.append(_))

      // update persisted invoices
      invoicesRef set transientUpdate.invoices

      self.reply(UpdateSuccess())
    }
  }
}

object InvoiceUpdateProcessor {
  case class Run()
  case class UpdateSuccess()
  case class UpdateFailure()
}
