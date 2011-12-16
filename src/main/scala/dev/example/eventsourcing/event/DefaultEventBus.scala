package dev.example.eventsourcing.event

import akka.actor._

class DefaultEventBus extends EventBus {
  private val subscriberRegistry = Actor.actorOf(new SubscriberRegistry).start

  def subscribe(subscriber: ActorRef, topic: String) =
    subscriberRegistry ! Subscribe(subscriber, topic)

  def unsubscribe(subscriber: ActorRef, topic: String) =
    subscriberRegistry ! Unsubscribe(subscriber, topic)

  def publish(event: Event, topic: String) =
    subscriberRegistry ! Publish(event, topic)

  private class SubscriberRegistry extends Actor {
    var subscribers = Map.empty[String, List[ActorRef]]

    def receive = {
      case Subscribe(subscriber, topic) => {
        subscribers.get(topic) match {
          case Some(sl) => subscribers = subscribers + (topic -> (subscriber :: sl))
          case None     => subscribers = subscribers + (topic -> List(subscriber))
        }
      }
      case Publish(event, topic) => {
        subscribers.get(topic) match {
          case Some(sl) => sl.foreach(_ ! event)
          case None     => ()
        }
      }
    }
  }

  private case class Subscribe(subscriber: ActorRef, topic: String)
  private case class Unsubscribe(subscriber: ActorRef, topic: String)
  private case class Publish(event: Event, topic: String)
}