package dev.example.eventsourcing.domain

trait Handler[E, +A] {
  def handle : PartialFunction[E, A]

  def update[B](event: E, handler: PartialFunction[E, B]): Update[E, B] =
    Update.accept(event, handler(event))
}
