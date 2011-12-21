package dev.example.eventsourcing.event

import akka.actor._

class DefaultEventBus extends EventBus {
  private val registry = Actor.actorOf(new SubscriberRegistry).start
  private val resequencer = Actor.actorOf(new Resequencer(registry)).start

  def subscribe(subscriber: Subscriber) =
    registry ! Subscribe(subscriber)

  def unsubscribe(subscriber: Subscriber) =
    registry ! Unsubscribe(subscriber)

  def publish(entry: EventLogEntry) =
    resequencer ! entry

  private class SubscriberRegistry extends Actor {
    var subscribers = List.empty[Subscriber]

    def receive = {
      case Subscribe(subscriber) => subscribers = subscriber :: subscribers
      case entry: EventLogEntry => subscribers foreach { subscriber =>
        subscriber.handle(entry)
      }
    }
  }

  private class Resequencer(target: ActorRef) extends Actor {
    var resequencers = Map.empty[Long, ActorRef] // one resequencer per logId

    def receive = {
      case entry: EventLogEntry => resequencer(entry.logId) forward entry
    }

    def resequencer(logId: Long) = resequencers.get(logId) match {
      case Some(resequencer) => resequencer
      case None              => {
        resequencers = resequencers + (logId -> Actor.actorOf(new EventLogEntryResequencer(target)).start)
        resequencers(logId)
      }
    }
  }

  private case class Subscribe(subscriber: Subscriber)
  private case class Unsubscribe(subscriber: Subscriber)
}