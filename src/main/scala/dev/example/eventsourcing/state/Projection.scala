package dev.example.eventsourcing.state

import akka.actor._
import akka.agent.Agent
import akka.dispatch._
import akka.stm._

import scalaz._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.event._

trait Projection[S, A] {
  def initialState: S
  def currentState: S

  def projectionLogic: PartialFunction[(S, A), S]
}

trait UpdateProjection[S, A] extends Projection[S, A] {
  private lazy val ref = Ref(initialState)
  private lazy val updater = Actor.actorOf(new Updater).start

  def eventLog: EventLog
  def writeAhead: Boolean = true

  def currentState: S = ref()

  def transacted(update: S => Update[Event, A]): Future[DomainValidation[A]] = {
    val promise = new DefaultCompletableFuture[DomainValidation[A]]()
    def dispatch = updater ! ApplyUpdate(update, promise)

    if (Stm.activeTransaction) {
      currentState // join
      deferred(dispatch)
    } else {
      dispatch
    }
    promise
  }

  private case class ApplyUpdate(update: S => Update[Event, A], promise: CompletableFuture[DomainValidation[A]])

  private class Updater extends Actor {
    if (writeAhead) self.dispatcher = Dispatchers.newThreadBasedDispatcher(self)

    def receive = {
      case ApplyUpdate(u, p) => {
        val current = currentState
        val update = u(current)

        update() match {
          case (events, s @ Success(result)) => {
            log(events.reverse) // TODO: handle errors
            ref set projectionLogic(current, result)
            p.completeWithResult(s)
          }
          case (_, f) => {
            p.completeWithResult(f)
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
  private lazy val agent = Agent(Snapshot(-1L, -1L, initialState))

  def currentState: S = currentSnapshot.state

  def currentSnapshot = agent()

  def receive(entry: EventLogEntry) = update(entry)

  def handles(event: Event) = projectionLogic.isDefinedAt((null.asInstanceOf[S], event))

  def update(entry: EventLogEntry) = if (handles(entry.event)) agent send { snapshot =>
    Snapshot(entry.logId, entry.logEntryId, projectionLogic(snapshot.state, entry.event))
  }

  def replay(eventLog: EventLog) = currentSnapshot match {
    case Snapshot(-1L, _, _) =>
      eventLog.iterator.foreach(update)
    case Snapshot(fromLogId, fromLogEntryId, event) => {
      val iterator = eventLog.iterator(fromLogId, fromLogEntryId)
      iterator.next() // ignore already processed event
      iterator.foreach(update)
    }
  }

  protected def await() = agent.await()
}
