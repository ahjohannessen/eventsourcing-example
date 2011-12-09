package dev.example.eventsourcing.server

import java.io.File

import com.twitter.zookeeper.ZooKeeperClient

import org.apache.zookeeper.CreateMode
import org.apache.bookkeeper.proto.BookieServer

object Bookkeeper extends App {
  val client = new ZooKeeperClient("localhost:2181")

  if (client.getHandle.exists("/ledgers", false) == null) {
    client.create("/ledgers", null, CreateMode.PERSISTENT)
    client.create("/ledgers/available",null, CreateMode.PERSISTENT)
  }

  1 to 4 foreach { i =>
    val journalPath = new File("target/bookkeeper/bookie%s/journal" format i)
    val ledgerPath = new File("target/bookkeeper/bookie%s/ledger" format i)

    journalPath.mkdirs()
    ledgerPath.mkdirs()

    new BookieServer(3180 + i, "localhost:2181", journalPath, Array(ledgerPath)).start
  }

}