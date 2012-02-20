package dev.example.eventsourcing.event

import akka.actor._


trait Channel[A] {
  def subscribe(subscriber: ChannelSubscriber[A])
  def unsubscribe(subscriber: ChannelSubscriber[A])

  def publish(message: A)
}

class SimpleChannel[A](system: ActorSystem) extends Channel[A] {
  private val registry = system.actorOf(Props(new SubscriberRegistry))

  def subscribe(subscriber: ChannelSubscriber[A]) =
    registry ! Subscribe(subscriber)

  def unsubscribe(subscriber: ChannelSubscriber[A]) =
    registry ! Unsubscribe(subscriber)

  def publish(message: A) =
    registry ! message

  private class SubscriberRegistry extends Actor {
    var subscribers = List.empty[ChannelSubscriber[A]]

    def receive = {
      case Subscribe(subscriber)   => subscribers = subscriber :: subscribers
      case Unsubscribe(subscriber) => subscribers.filterNot(_ == subscriber)
      case message: A              => subscribers foreach { subscriber => subscriber.receive(message) }
    }
  }

  private case class Subscribe(subscriber: ChannelSubscriber[A])
  private case class Unsubscribe(subscriber: ChannelSubscriber[A])
}

trait ChannelSubscriber[A] {
  def receive(message: A)
}