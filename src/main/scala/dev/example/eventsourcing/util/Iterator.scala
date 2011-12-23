package dev.example.eventsourcing.util

object Iterator {
  class EmptyIterator[A] extends Iterator[A] {
    def hasNext = false
    def next() = throw new NoSuchElementException
  }
}