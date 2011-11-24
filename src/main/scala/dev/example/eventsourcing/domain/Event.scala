package dev.example.eventsourcing.domain

trait Event

trait EventSourced[E <: Event, A <: EventSourced[E, A]] {
  def update(event: E): Update[E, A] = Update.accept(event, handle(event))
  def handle(event: E): A
}
