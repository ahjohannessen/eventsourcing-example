package dev.example.eventsourcing.state

import scala.collection.immutable.Queue

import akka.actor._
import akka.dispatch._
import akka.stm._

import scalaz._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._

trait Stateful[S, E <: Event, A] {
  private val stateChangesRef = Ref(Queue.empty[TransientUpdate[S, E, A]])

  def stateRef: Ref[S]
  def eventLog: EventLog[E]

  private lazy val processor =
    Actor.actorOf(new TransientUpdateProcessor[S, E, A](stateRef, stateChangesRef, eventLog)).start

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
}

case class TransientUpdate[S, E <: Event, A](
  state: S,
  events: List[E],
  result: DomainValidation[A],
  promise: CompletableFuture[DomainValidation[A]]) {

  def respond() = promise.completeWithResult(result)
}

class TransientUpdateProcessor[S, E <: Event, R](
  stateRef: Ref[S],
  stateChangesRef: Ref[Queue[TransientUpdate[S, E, R]]],
  eventLog: EventLog[E]) extends Actor {

  def receive = {
    case "run" => {
      val update = stateChangesRef().head

      if (update.result.isSuccess)
        update.events.reverse.foreach(eventLog.append)

      // TODO: handle write error to eventLog
      // - respond with error message and then
      // - for any subsequent update that was derived from
      //   a queued update, continue responding an error
      // - for the first subsequent update that was derived
      //   from Stateful.stateRef directly, retry appending
      //   to event log again
      //
      // Assumption: EventLog impl internally recovers from write errors
      //

      atomic {
        stateChangesRef alter ( queue => queue.dequeue._2)
        if (update.result.isSuccess)
          stateRef set update.state
        deferred { update.respond }
      }
    }
  }
}


