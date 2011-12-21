package dev.example.eventsourcing.event

import akka.actor._

class DefaultEventBus extends EventBus {
  private val registry = Actor.actorOf(new SubscriberRegistry).start
  private val resequencer = Actor.actorOf(new Resequencer(registry)).start

  def subscribe(subscriber: Subscriber, topic: String) =
    resequencer ! Subscribe(subscriber, topic)

  def unsubscribe(subscriber: Subscriber, topic: String) =
    resequencer ! Unsubscribe(subscriber, topic)

  def publish(event: Event, topic: String) =
    resequencer ! Publish(event, topic)

  private class SubscriberRegistry extends Actor {
    var subscribers = Map.empty[String, List[Subscriber]]

    def receive = {
      case Subscribe(subscriber, topic) => {
        subscribers.get(topic) match {
          case Some(sl) => subscribers = subscribers + (topic -> (subscriber :: sl))
          case None     => subscribers = subscribers + (topic -> List(subscriber))
        }
      }
      case Publish(event, topic) => {
        subscribers.get(topic) match {
          case Some(sl) => sl.foreach(_.onEvent(event))
          case None     => ()
        }
      }
      case entry: EventLogEntry => subscribers.get("log").foreach { subscribers =>
        subscribers.foreach(_.onEvent(EventLogged(entry)))
      }
    }
  }

  private class Resequencer(target: ActorRef) extends Actor {
    var resequencers = Map.empty[Long, ActorRef] // one resequencer per logId

    def receive = {
      case Publish(EventLogged(e @ EventLogEntry(logId, _, _)), "log") => resequencer(logId) forward e
      case other => target forward other
    }

    def resequencer(logId: Long) = resequencers.get(logId) match {
      case Some(resequencer) => resequencer
      case None              => {
        resequencers = resequencers + (logId -> Actor.actorOf(new EventLogEntryResequencer(target)).start)
        resequencers(logId)
      }
    }
  }

  private case class Subscribe(subscriber: Subscriber, topic: String)
  private case class Unsubscribe(subscriber: Subscriber, topic: String)
  private case class Publish(event: Event, topic: String)
}