package dev.example.eventsourcing.domain

trait Aggregate[+E, +A <: Aggregate[E, A]] {
  import Aggregate._

  def id: String
  def version: Long

  def require(version: Long): Update[E, A] =
    if (this.version == version)
      Update.accept(this.asInstanceOf[A])
    else
      Update.reject(invalidVersionError(version, this.version))

  def require(versionOption: Option[Long]): Update[E, A] = versionOption match {
    case Some(v) => require(v)
    case None    => Update.accept(this.asInstanceOf[A])
  }
}

object Aggregate {
  val template = "expected version = %s, actual version = %s"

  def invalidVersionError(expected: Long, actual: Long) =
    DomainError(template format (expected, actual))
}
