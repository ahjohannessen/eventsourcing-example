package dev.example.eventsourcing.service

import scala.collection.immutable.Queue

import akka.actor._
import akka.dispatch._
import akka.stm._

import scalaz._
import Scalaz._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.log.EventLog

abstract class DomainService[E <: Event, A <: Aggregate[E, A]](eventLog: EventLog[E]) {
  import DomainService._

  def domainObjectsRef: Ref[Map[String, A]]
  def domainUpdatesRef: Ref[List[TransientUpdate[E, A]]]

  lazy val updateProcessor = Actor.actorOf(new UpdateProcessor(domainObjectsRef, domainUpdatesRef, eventLog)).start

  def transactedUpdate(objectId: String)(f: (Option[A], Option[A]) => Update[E, A]) = atomic {
    val persistedDomainObjects = domainObjectsRef()
    val transientDomainObjects = domainUpdatesRef().headOption.map(_.domainObjects).getOrElse(persistedDomainObjects)

    val future = new DefaultCompletableFuture[DomainValidation[A]]

    f(persistedDomainObjects.get(objectId), transientDomainObjects.get(objectId))() match {
      case (events, Failure(errors))  => future.completeWithResult(Failure(errors))
      case (events, Success(updated)) => {
        domainUpdatesRef alter { list => TransientUpdate(transientDomainObjects + (objectId -> updated), events) :: list }
        deferred {
          updateProcessor ? UpdateProcessor.Run() onResult {
            case UpdateProcessor.UpdateSuccess() => future.completeWithResult(Success(updated))
            case UpdateProcessor.UpdateFailure() => () // TODO: report update/persistence error
          }
        }
      }
    }
    future
  }
}

object DomainService {
  case class TransientUpdate[+E, +A <: Aggregate[E, A]](domainObjects: Map[String, A], events: List[E])
}