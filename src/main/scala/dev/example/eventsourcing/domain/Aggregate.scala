package dev.example.eventsourcing.domain

trait Aggregate[+E, +A <: Aggregate[E, A]] {
  import Aggregate._

  def id: String
  def version: Long

  def require(expectedVersionOption: Option[Long], persistentVersion: Long) = {
    val transientVersion = version
    expectedVersionOption match {
      case Some(expectedVersion) if (expectedVersion != persistentVersion) =>
        Update.reject(wrongVersionError(id, expectedVersion, persistentVersion))
      case Some(expectedVersion) if ((expectedVersion == persistentVersion) && (persistentVersion < transientVersion)) =>
        Update.reject(conflictingUpdateError(id, persistentVersion))
      case Some(expectedVersion) if ((expectedVersion == persistentVersion) && (persistentVersion == transientVersion)) =>
        Update.accept(this.asInstanceOf[A])
      case None =>
        Update.accept(this.asInstanceOf[A])
    }
  }
}

object Aggregate {
  val wrongVersionTemplate = "invoice %s: expected version %s doesn't match current version %s"
  val conflictingUpdateTemplate = "invoice %s: conflicting update on version %s in progress"

  def wrongVersionError(invoiceId: String, expected: Long, current: Long) =
    DomainError(wrongVersionTemplate format (invoiceId, expected, current))

  def conflictingUpdateError(invoiceId: String, current: Long) =
    DomainError(conflictingUpdateTemplate format (invoiceId, current))
}
