package controllers

import com.sun.jersey.api.json._

import play.api._
import play.api.mvc._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.domain.Adapter._

import temp._

object Application extends Controller with JaxbSupport {
  val paths = "dev.example.eventsourcing.domain:dev.example.eventsourcing.web"
  val config = JSONConfiguration.mapped().rootUnwrapping(false).build()

  // can be used for JAXB-based JSON and XML processing
  implicit val jsonJaxbContext = new JSONJAXBContext(config, paths)

  val invoice = DraftInvoice("123", 17, List(
    InvoiceItem("a", 1, 1),
    InvoiceItem("b", 2, 2)
  ))

  def index = Action {
    Ok(views.html.index("Work in progress ..."))
  }

  def foo = Action { implicit request =>
    Ok(Jaxb(Invoices(List(invoice, invoice))))
  }

  def bar = Action(jaxb.parse[DraftInvoice]) { implicit request =>
    val invoice: DraftInvoice = request.body
    Ok(Jaxb(invoice.copy(version = invoice.version + 1)))
  }
}


