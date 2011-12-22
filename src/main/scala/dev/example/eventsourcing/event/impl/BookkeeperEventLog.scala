package dev.example.eventsourcing.event.impl

import java.util.concurrent.atomic.AtomicLong

import akka.dispatch._

import org.apache.bookkeeper.client.AsyncCallback.AddCallback
import org.apache.bookkeeper.client.BookKeeper.DigestType
import org.apache.bookkeeper.client._

import dev.example.eventsourcing.event._
import dev.example.eventsourcing.util.Serialization._

class BookkeeperEventLog extends EventLog {
  private val bookkeeper = new BookKeeper("localhost:2181")

  private val secret = "secret".getBytes
  private val digest = DigestType.MAC

  val readLogId = BookkeeperEventLog.maxLogId

  val writeLog = createLog()
  val writeLogId = writeLog.getId
  val writeCounter = new AtomicLong(-1L)

  def iterator(fromLogId: Long, fromLogEntryId: Long): Iterator[EventLogEntry] =
    if (fromLogId <= readLogId) new EventIterator(fromLogId, fromLogEntryId) else new EmptyIterator

  def appendAsync(event: Event): Future[EventLogEntry] = {
    val promise = new DefaultCompletableFuture[EventLogEntry]()
    writeLog.asyncAddEntry(serialize(event), new AddCallback {
      def addComplete(rc: Int, lh: LedgerHandle, entryId: Long, ctx: AnyRef) {
        if (rc == 0) promise.completeWithResult(EventLogEntry(writeLogId, entryId, writeCounter.incrementAndGet(), event))
        else         promise.completeWithException(BKException.create(rc))
      }
    }, null)
    promise
  }

  private def createLog() =
    bookkeeper.createLedger(digest, secret)

  private def openLog(logId: Long) =
    bookkeeper.openLedger(logId, digest, secret)

  private class EventIterator(fromLogId: Long, fromLogEntryId: Long) extends Iterator[EventLogEntry] {
    import scala.collection.JavaConverters._

    // TODO: do not hold all entries of a ledger in memory
    var currentIterator = iteratorFor(fromLogId, fromLogEntryId)
    var currentLogId = fromLogId

    val readCounter = new AtomicLong(-1L)

    def toEventLogEntry(entry: LedgerEntry) =
      EventLogEntry(entry.getLedgerId, entry.getEntryId, readCounter.incrementAndGet(), deserialize(entry.getEntry).asInstanceOf[Event])

    def iteratorFor(logId: Long, fromLogEntryId: Long) = {
      var log: LedgerHandle = null
      try {
        log = openLog(logId)
        if (log.getLastAddConfirmed == -1) new EmptyIterator
        else log.readEntries(fromLogEntryId, log.getLastAddConfirmed).asScala.toList.map(toEventLogEntry).toIterator
      } catch {
        case e => new EmptyIterator // TODO: log error
      } finally {
        if (log != null) log.close()
      }
    }

    def hasNext: Boolean = {
      if      ( currentIterator.hasNext) true
      else if (!currentIterator.hasNext && currentLogId == readLogId) false
      else {
        currentLogId = currentLogId + 1
        currentIterator = iteratorFor(currentLogId, 0L)
        readCounter.set(-1L)
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

object BookkeeperEventLog {
  private val zookeeper = new com.twitter.zookeeper.ZooKeeperClient("localhost:2181")

  val maxLogId = zookeeper.getChildren("/ledgers").filter(_.startsWith("L")).map(_.substring(1).toLong)
    .foldLeft(-1L) { (z, a) => if (a > z) a else z }
}