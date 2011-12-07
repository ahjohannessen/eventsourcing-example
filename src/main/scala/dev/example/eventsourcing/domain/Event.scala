package dev.example.eventsourcing.domain

trait Event

trait EventSourced[+E, +A <: EventSourced[E, A]] {
  def update[EE >: E](event: EE): Update[EE, A] = Update.accept(event, handle(event))
  def handle[EE >: E](event: EE): A
}
