package dev.example.eventsourcing.log

import akka.actor._
import akka.stm._

import dev.example.eventsourcing.domain.Event

trait EventLog {
  val events = Ref[List[Event]](Nil)
  val eventStore: ActorRef

  def append(events: List[Event]) {
    this.events alter { current => events ::: current }
  }

  def store(/* TODO timeout */) =
    eventStore ? StoreEvents() await

  def storeAsync() =
    eventStore ! StoreEvents()
}

trait EventStore { this: Actor =>
  val events: Ref[List[Event]]

  def drain() = events update Nil

  def receive: Receive = {
    case StoreEvents() => {
      drain().reverse foreach store
      self.channel tryTell StoreEventsResponse()
    }
  }

  def store(event: Event)
}

case class StoreEvents()
case class StoreEventsResponse()
