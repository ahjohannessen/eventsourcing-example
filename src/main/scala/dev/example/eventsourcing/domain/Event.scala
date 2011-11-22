package dev.example.eventsourcing.domain

trait Event

trait EventSourced[A <: EventSourced[A]] {
  def update(event: Event): Update[A] = Update.accept(event, handle(event))
  def handle(event: Event): A
}
