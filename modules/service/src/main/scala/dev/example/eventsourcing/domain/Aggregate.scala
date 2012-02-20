package dev.example.eventsourcing.domain

trait Aggregate[+A] {
  import Aggregate._

  def id: String
  def version: Long

  def versionOption = if (version == -1L) None else Some(version)

  def require(expectedVersion: Option[Long]) = {
    expectedVersion match {
      case Some(expected) if (version != expected) =>
        Update.reject(invalidVersionError(id, expected, version))
      case Some(expected) if (version == expected) =>
        Update.accept(this.asInstanceOf[A])
      case None =>
        Update.accept(this.asInstanceOf[A])
    }
  }
}

object Aggregate {
  val invalidVersionTemplate = "invoice %s: expected version %s doesn't match current version %s"

  def invalidVersionError(invoiceId: String, expected: Long, current: Long) =
    DomainError(invalidVersionTemplate format (invoiceId, expected, current))
}
