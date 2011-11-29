package dev.example.eventsourcing.log

import akka.actor._
import akka.stm._

import dev.example.eventsourcing.domain.Event

trait EventLog[E <: Event] {
  val eventsRef = Ref[List[E]](Nil)
  val eventStore: ActorRef

  def log(events: List[E]) {
    eventsRef alter { current => events ::: current }
  }

  def store(/* TODO timeout */) =
    eventStore ? StoreEvents() await

  def storeAsync() =
    eventStore ! StoreEvents()
}

trait EventStore[E <: Event] { this: Actor =>
  val eventsRef: Ref[List[E]]

  def drain() = eventsRef update Nil

  def receive: Receive = {
    case StoreEvents() => {
      drain().reverse foreach store
      self.channel tryTell StoreEventsResponse()
    }
  }

  def store(event: E)
}

case class StoreEvents()
case class StoreEventsResponse()
