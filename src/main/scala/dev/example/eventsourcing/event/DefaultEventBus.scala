package dev.example.eventsourcing.event

import akka.actor._

class DefaultEventBus extends EventBus {
  private val subscriberRegistry = Actor.actorOf(new SubscriberRegistry).start

  def subscribe(subscriber: Subscriber, topic: String) =
    subscriberRegistry ! Subscribe(subscriber, topic)

  def unsubscribe(subscriber: Subscriber, topic: String) =
    subscriberRegistry ! Unsubscribe(subscriber, topic)

  def publish(event: Event, topic: String) =
    subscriberRegistry ! Publish(event, topic)

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
    }
  }

  private case class Subscribe(subscriber: Subscriber, topic: String)
  private case class Unsubscribe(subscriber: Subscriber, topic: String)
  private case class Publish(event: Event, topic: String)
}