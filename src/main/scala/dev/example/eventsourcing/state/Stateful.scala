package dev.example.eventsourcing.state

import akka.actor._
import akka.dispatch._
import akka.stm._

import scalaz._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._

trait Stateful[S, E <: Event, A] {
  private lazy val processor = Actor.actorOf(new UpdateRequestProcessor).start

  def stateRef: Ref[S]
  def eventLog: EventLog[E]

  def transacted(update: S => Update[E, A], transition: (S, A) => S): Future[DomainValidation[A]] =
    (processor ? UpdateRequest(update, transition)).asInstanceOf[Future[DomainValidation[A]]]

  case class UpdateRequest(update: S => Update[E, A], transition: (S, A) => S)

  class UpdateRequestProcessor extends Actor {
    def receive = {
      case UpdateRequest(u, t) => {
        val state = stateRef()
        val delta = u(state)

        delta() match {
          case (events, s @ Success(r)) => {
            log(events.reverse)
            stateRef set t(state, r)
            self.reply(s)
          }
          case (_, f) => {
            self.reply(f)
          }
        }
      }
    }

    def log(events: List[E]) = events.foreach(eventLog.append)
  }
}




