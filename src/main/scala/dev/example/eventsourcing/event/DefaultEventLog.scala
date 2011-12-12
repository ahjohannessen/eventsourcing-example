package dev.example.eventsourcing.event

import com.twitter.zookeeper.ZooKeeperClient

import org.apache.bookkeeper.client.AsyncCallback.AddCallback
import org.apache.bookkeeper.client.BookKeeper.DigestType
import org.apache.bookkeeper.client.{LedgerHandle, BookKeeper}
import java.util.concurrent.Exchanger

object DefaultEventLog extends EventLog[Event] {
  private val secret = "secret".getBytes
  private val digest = DigestType.MAC

  private val zookeeper = new ZooKeeperClient("localhost:2181")
  private val bookkeeper = new BookKeeper("localhost:2181")

  val currentLog = createLog()

  def append(event: Event): Long = {
    val exchanger = new Exchanger[Long]()
    appendAsync(event: Event) { entryId => exchanger.exchange(entryId) }
    exchanger.exchange(0)
  }

  // TODO: use proper event format for logging
  def appendAsync(event: Event)(f: Long => Unit) {
    currentLog.asyncAddEntry(event.toString.getBytes, new AddCallback {
      def addComplete(rc: Int, lh: LedgerHandle, entryId: Long, ctx: AnyRef) {
        f(entryId)
      }
    }, null)
  }

  private def createLog() =
    bookkeeper.createLedger(digest, secret)

  private def openLog(logId: Long) =
    bookkeeper.openLedger(logId, digest, secret)

  private def currentLogId: Option[Long] = maxLogId() match {
    case -1L => None
    case  id => Some(id)
  }

  private def latestLogId: Option[Long] = maxLogId match {
    case -1L => None
    case  0L => None
    case  id => Some(id)
  }

  private def maxLogId(): Long = zookeeper.getChildren("/ledgers")
    .filter(_.startsWith("L")).map(_.substring(1).toLong).foldLeft(-1L) { (z, a) => if (a > z) a else z }
}
