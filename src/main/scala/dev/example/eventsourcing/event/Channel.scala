package dev.example.eventsourcing.event

import akka.actor.Actor

trait Channel[A] {
  def subscribe(subscriber: ChannelSubscriber[A])
  def unsubscribe(subscriber: ChannelSubscriber[A])

  def publish(message: A)
}

class SimpleChannel[A] extends Channel[A] {
  private val registry = Actor.actorOf(new SubscriberRegistry).start

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