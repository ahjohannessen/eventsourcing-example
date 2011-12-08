package dev.example.eventsourcing.service

import akka.actor._
import akka.stm._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.log.EventLog

import DomainService.TransientUpdate

class UpdateProcessor[E <: Event, A <: Aggregate[E, A]](
    domainObjectsRef: Ref[Map[String, A]],
    domainUpdatesRef: Ref[List[TransientUpdate[E, A]]],
    eventLog: EventLog[E]) extends Actor {

  import UpdateProcessor._

  def receive = {
    case Run() => {
      val transientUpdates = domainUpdatesRef()
      val transientUpdatesCount = transientUpdates.length

      transientUpdates.reverse foreach { transientUpdate =>
        transientUpdate.events.reverse.foreach(eventLog.append(_))
      }

      if (transientUpdatesCount > 0) atomic {
        domainUpdatesRef alter (list => list.reverse.drop(transientUpdatesCount).reverse)
        domainObjectsRef update transientUpdates.head.domainObjects // apply last update
      }

      self.reply(UpdateSuccess())
    }
  }
}

object UpdateProcessor {
  case class Run()
  case class UpdateSuccess()
  case class UpdateFailure()
}
