package dev.example.eventsourcing.server

import org.apache.zookeeper.server.quorum.QuorumPeerMain

object Zookeeper extends App {
  QuorumPeerMain.main(Array("src/main/resources/zoo.cfg"))
}