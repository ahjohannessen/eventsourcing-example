package dev.example.eventsourcing.state

import akka.actor._
import akka.dispatch._
import akka.stm._

import scalaz._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._

trait Stateful[S, E <: Event, A] {
  private lazy val updater = Actor.actorOf(new Updater).start

  def stateRef: Ref[S]
  def eventLog: EventLog[E]
  def apply(updated: A)

  def transacted(update: S => Update[E, A]): Future[DomainValidation[A]] =
    (updater ? ApplyUpdate(update)).asInstanceOf[Future[DomainValidation[A]]]

  private case class ApplyUpdate(update: S => Update[E, A])

  private class Updater extends Actor {
    def receive = {
      case ApplyUpdate(u) => {
        val state = stateRef()
        val delta = u(state)

        delta() match {
          case (events, s @ Success(r)) => {
            log(events.reverse)
            Stateful.this(r)
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




