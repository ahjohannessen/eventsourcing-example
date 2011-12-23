package dev.example.eventsourcing

package object event {
  val ignore: PartialFunction[Event, Unit] = {
    case event => ()
  }
}