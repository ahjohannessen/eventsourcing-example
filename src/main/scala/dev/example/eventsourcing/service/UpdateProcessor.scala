package dev.example.eventsourcing.service

import scala.collection.immutable.Queue

import akka.actor._
import akka.stm._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.log.EventLog

import DomainService.TransientUpdate

class UpdateProcessor[E <: Event, A <: Aggregate[E, A]](
    domainObjectsRef: Ref[Map[String, A]],
    domainUpdatesRef: Ref[Queue[TransientUpdate[E, A]]],
    eventLog: EventLog[E]) extends Actor {

  import UpdateProcessor._

  protected def receive = {
    case Run() => {
      // dequeue updated transient domain objects
      val transientUpdate = atomic {
        val (u, q) = domainUpdatesRef().dequeue
        domainUpdatesRef set q
        u
      }

      // persist events // TODO: failure recovery
      transientUpdate.events.reverse.foreach(eventLog.append(_))

      // update domain objects reference
      domainObjectsRef set transientUpdate.domainObjects

      self.reply(UpdateSuccess())
    }
  }
}

object UpdateProcessor {
  case class Run()
  case class UpdateSuccess()
  case class UpdateFailure()
}
