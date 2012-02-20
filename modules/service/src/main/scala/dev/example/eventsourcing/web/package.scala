package dev.example.eventsourcing

import javax.xml.bind.annotation._

import dev.example.eventsourcing.domain.DomainError

package object web {
  @XmlRootElement(name = "app-error")
  case class AppError(errors: DomainError) {
    import scala.collection.JavaConverters._

    def this() = this(null)

    @XmlElement
    def getMessage: java.util.List[String] = errors.asJava
  }

  @XmlRootElement(name = "sys-error")
  @XmlAccessorType(XmlAccessType.FIELD)
  case class SysError(message: String) {
    def this() = this(null)
  }

  object SysError {
    val NotFound = SysError("Not Found")
  }
}