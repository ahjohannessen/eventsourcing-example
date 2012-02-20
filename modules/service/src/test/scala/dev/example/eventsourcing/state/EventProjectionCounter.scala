package dev.example.eventsourcing.state

import java.util.concurrent.CountDownLatch

import dev.example.eventsourcing.event._

trait EventProjectionCounter[S] extends EventProjection[S] {
  @volatile var latch: CountDownLatch = _

  def expect(count: Int) {
    latch = new CountDownLatch(count)
  }

  override def await() = {
    latch.await()
    super.await()
  }

  abstract override def update(entry: EventLogEntry) = {
    super.update(entry)
    latch.countDown()
  }
}

