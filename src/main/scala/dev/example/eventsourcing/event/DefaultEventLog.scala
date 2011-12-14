package dev.example.eventsourcing.event

import java.util.concurrent.Exchanger

import org.apache.bookkeeper.client.AsyncCallback.AddCallback
import org.apache.bookkeeper.client.BookKeeper.DigestType
import org.apache.bookkeeper.client.{LedgerHandle, BookKeeper}

import dev.example.eventsourcing.util.Serialization._

object DefaultEventLog extends EventLog[Event] with Iterable[Event] {
  private val bookkeeper = new BookKeeper("localhost:2181")

  private val secret = "secret".getBytes
  private val digest = DigestType.MAC

  val writeLog = createLog()
  val writeLogId = writeLog.getId

  def iterator: Iterator[Event] = iterator(1L, 0L)

  def iteratorOf[E](implicit m: Manifest[E]): Iterator[E] = iteratorOf[E](1L, 0L)

  def iterator(fromLogId: Long, fromLogEntryId: Long): Iterator[Event] =
    if (fromLogId < writeLogId) new EventIterator(fromLogId, fromLogEntryId) else new EmptyIterator

  def iteratorOf[E](fromLogId: Long, fromLogEntryId: Long)(implicit m: Manifest[E]): Iterator[E] =
    iterator(fromLogId, fromLogEntryId).filter(m.erasure.isInstance).map(_.asInstanceOf[E])

  def append(event: Event): Long = {
    val exchanger = new Exchanger[Long]()
    appendAsync(event: Event) { entryId => exchanger.exchange(entryId) }
    exchanger.exchange(0)
  }

  def appendAsync(event: Event)(f: Long => Unit) {
    writeLog.asyncAddEntry(serialize(event), new AddCallback {
      def addComplete(rc: Int, lh: LedgerHandle, entryId: Long, ctx: AnyRef) {
        f(entryId)
      }
    }, null)
  }

  private def createLog() =
    bookkeeper.createLedger(digest, secret)

  def openLog(logId: Long) =
    bookkeeper.openLedger(logId, digest, secret)

  private class EventIterator(fromLogId: Long, fromLogEntryId: Long) extends Iterator[Event] {
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
          log.readEntries(fromLogEntryId, log.getLastAddConfirmed).asScala.toList
            .map(entry => entry.getEntry)
            .map(entry => deserialize(entry).asInstanceOf[Event]).toIterator
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

  private class EmptyIterator extends Iterator[Event] {
    def hasNext = false
    def next() = throw new NoSuchElementException
  }
}
