package dev.example.eventsourcing.service

import scala.collection.immutable.Queue

import akka.actor._
import akka.dispatch._
import akka.stm._

import scalaz._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._

abstract class DomainService[E <: Event, A <: Aggregate[E, A]](eventLog: EventLog[E], initialState: Map[String, A]) {
  protected val domainObjectsRef = Ref(initialState)
  protected val domainUpdatesRef = Ref(Queue.empty[DomainUpdate[E, A]])

  private val updateProcessor = Actor.actorOf(new UpdateProcessor(domainObjectsRef, domainUpdatesRef, eventLog)).start

  protected def transactedUpdate(objectId: String)(f: (Option[A], Option[A]) => Update[E, A]) = atomic {
    val persistedDomainObjects = domainObjectsRef()
    val transientDomainObjects = domainUpdatesRef().lastOption.map(_.domainObjects).getOrElse(persistedDomainObjects)

    val future = new DefaultCompletableFuture[DomainValidation[A]]

    f(persistedDomainObjects.get(objectId), transientDomainObjects.get(objectId))() match {
      case (events, Failure(errors))  => future.completeWithResult(Failure(errors))
      case (events, Success(updated)) => {
        domainUpdatesRef alter { queue => queue enqueue DomainUpdate(transientDomainObjects + (objectId -> updated), events) }
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

private[service] case class DomainUpdate[E, +A <: Aggregate[E, A]](domainObjects: Map[String, A], events: List[E])
