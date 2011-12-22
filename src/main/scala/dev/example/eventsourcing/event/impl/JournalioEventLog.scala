package dev.example.eventsourcing.event.impl

import java.io.File
import java.util.concurrent.atomic.AtomicLong

import akka.dispatch._

import journal.io.api._

import dev.example.eventsourcing.event._
import dev.example.eventsourcing.util.Serialization._

/**
 * Highly experimental.
 */
class JournalioEventLog extends EventLog {
  import scala.collection.JavaConverters._

  val writeCounter = new AtomicLong(-1L)

  val journalDir = new File("target/journalio")
  val journal = new Journal

  journalDir.mkdirs()

  journal.setDirectory(journalDir)
  journal.setMaxFileLength(Int.MaxValue)
  journal.setMaxWriteBatchSize(Int.MaxValue)
  journal.open()


  def iterator = iterator(1L, 1L)

  def iterator(fromLogId: Long, fromLogEntryId: Long) = {
    val readCounter = new AtomicLong(-1L)
    journal.redo(new Location(fromLogId.toInt, fromLogEntryId.toInt)).asScala.iterator.map { location =>
      new EventLogEntry(
        location.getDataFileId,
        location.getPointer,
        readCounter.incrementAndGet(),
        deserialize(journal.read(location)).asInstanceOf[Event])
    }
  }

  def appendAsync(event: Event): Future[EventLogEntry] = {
    val promise = new DefaultCompletableFuture[EventLogEntry]
    val location = journal.write(serialize(event), true) // sync
    println("wrote %s" format location)
    promise.completeWithResult(EventLogEntry(
      location.getDataFileId,
      location.getPointer,
      writeCounter.incrementAndGet(),
      event))
  }
}