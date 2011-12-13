package dev.example.eventsourcing.state

import scala.collection.immutable.Queue

import akka.actor._
import akka.dispatch._
import akka.stm._

import scalaz._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._

trait StatefulAlternative[S, E <: Event, A] {
  private val stateChangesRef = Ref(Queue.empty[TransientUpdate])
  private lazy val processor = Actor.actorOf(new TransientUpdateProcessor).start

  def stateRef: Ref[S]
  def eventLog: EventLog[E]

  def transacted(update: S => Update[E, A], transition: (S, A) => S): Future[DomainValidation[A]] = atomic {
    val currentState = stateChangesRef().lastOption.map(_.state).getOrElse(stateRef())
    val promise = new DefaultCompletableFuture[DomainValidation[A]]

    val (events, result) = update(currentState)()
    val updatedState = result match {
      case Success(x) => transition(currentState, x)
      case failure    => currentState
    }

    stateChangesRef alter ( queue => queue.enqueue(TransientUpdate(updatedState, events, result, promise)))
    deferred { processor ! "run" }

    promise
  }

  case class TransientUpdate(
    state: S,
    events: List[E],
    result: DomainValidation[A],
    promise: CompletableFuture[DomainValidation[A]]) {

    def respond() = promise.completeWithResult(result)
  }

  class TransientUpdateProcessor extends Actor {
    def receive = {
      case "run" => {
        val update = stateChangesRef().head

        if (update.result.isSuccess)
          update.events.reverse.foreach(eventLog.append)

        atomic {
          stateChangesRef alter ( queue => queue.dequeue._2)
          if (update.result.isSuccess)
            stateRef set update.state
          deferred { update.respond }
        }
      }
    }
  }
}



