package dev.example.eventsourcing

import javax.ws.rs.core._
import javax.ws.rs.core.Response.Status._
import javax.xml.bind.annotation._

import com.sun.jersey.api.representation.Form

import dev.example.eventsourcing.domain.DomainError
import com.sun.jersey.api.view.Viewable

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
    val NotFound = SysError(NOT_FOUND.getReasonPhrase)
  }

  val rootPath = "/dev/example/eventsourcing"

  def errorPath(templateName: String) = "%s/error/%s" format (rootPath, templateName)
  def homePath(templateName: String) = "%s/home/%s" format (rootPath, templateName)
  def webPath(templateName: String) = "%s/web/%s" format (rootPath, templateName)

  def uri(path: String) = UriBuilder.fromPath(path).build()

  def sc200(entity: AnyRef) = Response.ok(entity).build()
  def sc201(entity: AnyRef, path: String) = Response.created(uri(path)).entity(entity).build()
  def sc404(entity: AnyRef) = Response.status(NOT_FOUND).entity(entity).build()
  def sc409(entity: AnyRef) = Response.status(CONFLICT).entity(entity).build()

  class RichForm(form: Form) {
    import scala.collection.JavaConverters._

    def toMap: Map[String, String] =
      form.asScala.foldLeft(Map.empty[String, String]) { (m, kv) => m + (kv._1 -> kv._2.get(0)) }
  }

  implicit def form2RichForm(form: Form) = new RichForm(form)
}