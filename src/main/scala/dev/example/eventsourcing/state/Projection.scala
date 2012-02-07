package dev.example.eventsourcing.state

import scala.concurrent.stm.{Txn, Ref}

import akka.actor._
import akka.agent.Agent
import akka.dispatch._
import akka.util.duration._

import scalaz._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._

trait Projection[S, A] {
  def initialState: S
  def currentState: S

  def project: PartialFunction[(S, A), S]
}

trait UpdateProjection[S, A] extends Projection[S, A] {
  private lazy val ref = Ref(initialState)
  private lazy val updater = system.actorOf(Props(new Updater)) // TODO: thread-based dispatcher if write-ahead

  def system: ActorSystem
  def eventLog: EventLog
  def writeAhead: Boolean = true

  def currentState: S = ref.single.get

  def transacted[B <: A](update: S => Update[Event, B]): Future[DomainValidation[B]] = {
    val promise = Promise[DomainValidation[B]]()(system.dispatcher)
    def dispatch = updater ! ApplyUpdate(update, promise.asInstanceOf[Promise[DomainValidation[A]]])

    val txn = Txn.findCurrent
    if (txn.isDefined) Txn.afterCommit(status ⇒ dispatch)(txn.get) else  dispatch
    promise
  }

  private case class ApplyUpdate(update: S => Update[Event, A], promise: Promise[DomainValidation[A]])

  private class Updater extends Actor {
    def receive = {
      case ApplyUpdate(u, p) => {
        val current = currentState
        val update = u(current)

        update() match {
          case (events, s @ Success(result)) => {
            log(events.reverse) // TODO: handle errors
            ref.single.transformAndGet(_ => project(current, result.asInstanceOf[A]))
            p.success(s)
          }
          case (_, f) => {
            p.success(f)
          }
        }
      }
    }

    def log(events: List[Event]) = events.foreach { event =>
      if (writeAhead) eventLog.append(event) else eventLog.appendAsync(event)
    }
  }
}

trait EventProjection[S] extends Projection[S, Event] with ChannelSubscriber[EventLogEntry] {
  private lazy val agent = Agent(Snapshot(-1L, -1L, initialState))(system)

  def system: ActorSystem

  def currentState: S = currentSnapshot.state

  def currentSnapshot = agent()

  def receive(entry: EventLogEntry) = update(entry)

  def handles(event: Event) = project.isDefinedAt((null.asInstanceOf[S], event))

  def update(entry: EventLogEntry) = if (handles(entry.event)) agent send { snapshot =>
    Snapshot(entry.logId, entry.logEntryId, project(snapshot.state, entry.event))
  }

  def recover(eventLog: EventLog) = currentSnapshot match {
    case Snapshot(-1L, _, _) =>
      eventLog.iterator.foreach(update)
    case Snapshot(fromLogId, fromLogEntryId, event) => {
      val iterator = eventLog.iterator(fromLogId, fromLogEntryId)
      iterator.next() // ignore already processed event
      iterator.foreach(update)
    }
  }

  protected def await() = agent.await(5.seconds) // TODO: make configurable
}
