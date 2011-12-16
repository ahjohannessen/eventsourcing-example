package dev.example.eventsourcing.event

import akka.actor.ActorRef

trait EventBus {
  def subscribe(subscriber: ActorRef, topic: String)
  def unsubscribe(subscriber: ActorRef, topic: String)

  def publish(event: Event, topic: String)
}