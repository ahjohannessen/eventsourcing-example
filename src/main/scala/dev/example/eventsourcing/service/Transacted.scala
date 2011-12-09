package dev.example.eventsourcing.service

import scala.collection.immutable.Queue

import akka.actor._
import akka.dispatch._
import akka.stm._

import scalaz._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._

trait Transacted[E <: Event, A] {
  private val transientUpdatesRef =
    Ref(Queue.empty[TransientUpdate[E, A]])

  private lazy val updateProcessor =
    Actor.actorOf(new TransientUpdateProcessor[E, A](domainObjectsRef, transientUpdatesRef, eventLog)).start

  def domainObjectsRef: Ref[Map[String, A]]
  def eventLog: EventLog[E]

  def update(objectId: String)(f: (Option[A], Option[A]) => Update[E, A]) = atomic {
    val persistedDomainObjects = domainObjectsRef()
    val transientDomainObjects = transientUpdatesRef().lastOption.map(_.domainObjects).getOrElse(persistedDomainObjects)

    val future = new DefaultCompletableFuture[DomainValidation[A]]

    f(persistedDomainObjects.get(objectId), transientDomainObjects.get(objectId))() match {
      case (events, Failure(errors))  => future.completeWithResult(Failure(errors))
      case (events, Success(updated)) => {
        transientUpdatesRef alter { queue => queue enqueue TransientUpdate(transientDomainObjects + (objectId -> updated), events) }
        deferred {
          updateProcessor ? TransientUpdateProcessor.Run() onResult {
            case TransientUpdateProcessor.UpdateSuccess() => future.completeWithResult(Success(updated))
            case TransientUpdateProcessor.UpdateFailure() => () // TODO: report update/persistence error
          }
        }
      }
    }
    future
  }
}

private[service] case class TransientUpdate[E, A](domainObjects: Map[String, A], events: List[E])

private[service] class TransientUpdateProcessor[E <: Event, A](
  persistedObjectsRef: Ref[Map[String, A]],
  transientUpdatesRef: Ref[Queue[TransientUpdate[E, A]]],
  eventLog: EventLog[E]) extends Actor {

  import TransientUpdateProcessor._

  def receive = {
    case Run() => {
      val transientUpdates = transientUpdatesRef()
      val transientUpdatesCount = transientUpdates.length

      transientUpdates foreach { transientUpdate =>
        transientUpdate.events.reverse.foreach(eventLog.append(_))
      }

      if (transientUpdatesCount > 0) atomic {
        transientUpdatesRef alter { queue => queue.drop(transientUpdatesCount) }
        persistedObjectsRef update transientUpdates.last.domainObjects // apply last update
      }

      self.reply(UpdateSuccess())
    }
  }
}

private[service] object TransientUpdateProcessor {
  case class Run()
  case class UpdateSuccess()
  case class UpdateFailure()
}

