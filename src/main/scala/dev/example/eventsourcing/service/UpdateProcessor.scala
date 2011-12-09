package dev.example.eventsourcing.service

import scala.collection.immutable.Queue

import akka.actor._
import akka.stm._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._

class UpdateProcessor[E <: Event, A <: Aggregate[E, A]](
  domainObjectsRef: Ref[Map[String, A]],
  domainUpdatesRef: Ref[Queue[DomainUpdate[E, A]]],
  eventLog: EventLog[E]) extends Actor {

  import UpdateProcessor._

  def receive = {
    case Run() => {
      val transientUpdates = domainUpdatesRef()
      val transientUpdatesCount = transientUpdates.length

      transientUpdates foreach { transientUpdate =>
        transientUpdate.events.reverse.foreach(eventLog.append(_))
      }

      if (transientUpdatesCount > 0) atomic {
        domainUpdatesRef alter { queue => queue.drop(transientUpdatesCount) }
        domainObjectsRef update transientUpdates.last.domainObjects // apply last update
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
