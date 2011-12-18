package dev.example.eventsourcing.event

import java.util.concurrent.Exchanger

import org.apache.bookkeeper.client.AsyncCallback.AddCallback
import org.apache.bookkeeper.client.BookKeeper.DigestType
import org.apache.bookkeeper.client.{LedgerHandle, BookKeeper}

import dev.example.eventsourcing.util.Serialization._

class DefaultEventLog extends EventLog {
  private val bookkeeper = new BookKeeper("localhost:2181")

  private val secret = "secret".getBytes
  private val digest = DigestType.MAC

  val writeLog = createLog()
  val writeLogId = writeLog.getId

  def iterator(fromLogId: Long, fromLogEntryId: Long): Iterator[EventLogEntry] =
    if (fromLogId < writeLogId) new EventIterator(fromLogId, fromLogEntryId) else new EmptyIterator

  def append(event: Event): EventLogEntry = {
    val exchanger = new Exchanger[EventLogEntry]()
    appendAsync(event: Event) { entry => exchanger.exchange(entry) }
    exchanger.exchange(null)
  }

  def appendAsync(event: Event)(f: EventLogEntry => Unit) {
    writeLog.asyncAddEntry(serialize(event), new AddCallback {
      def addComplete(rc: Int, lh: LedgerHandle, entryId: Long, ctx: AnyRef) {
        f(EventLogEntry(writeLogId, entryId, event))
      }
    }, null)
  }

  private def createLog() =
    bookkeeper.createLedger(digest, secret)

  private def openLog(logId: Long) =
    bookkeeper.openLedger(logId, digest, secret)

  private class EventIterator(fromLogId: Long, fromLogEntryId: Long) extends Iterator[EventLogEntry] {
    var currentIterator = iteratorFor(fromLogId, fromLogEntryId)
    var currentLogId = fromLogId

    def iteratorFor(logId: Long, fromLogEntryId: Long) = {
      var log: LedgerHandle = null
      try {
        log = openLog(logId)
        if (log.getLastAddConfirmed == -1) {
          new EmptyIterator
        } else {
          import scala.collection.JavaConverters._
          log.readEntries(fromLogEntryId, log.getLastAddConfirmed).asScala.toList.map { entry =>
            EventLogEntry(entry.getLedgerId, entry.getEntryId, deserialize(entry.getEntry).asInstanceOf[Event])
          }.toIterator
        }
      } catch {
        case e => new EmptyIterator // TODO: log error
      } finally {
        if (log != null) log.close()
      }
    }

    def hasNext: Boolean = {
      if      ( currentIterator.hasNext) true
      else if (!currentIterator.hasNext && currentLogId == writeLogId - 1) false
      else {
        currentLogId = currentLogId + 1
        currentIterator = iteratorFor(currentLogId, 0L)
        hasNext
      }
    }

    def next() = if (hasNext) currentIterator.next() else throw new NoSuchElementException
  }

  private class EmptyIterator extends Iterator[EventLogEntry] {
    def hasNext = false
    def next() = throw new NoSuchElementException
  }
}
