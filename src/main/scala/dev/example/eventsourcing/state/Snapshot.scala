package dev.example.eventsourcing.state

case class Snapshot[S](logId: Long, logEntryId: Long, state: S)
