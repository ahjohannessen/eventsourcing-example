package controllers

import com.sun.jersey.api.json._

import scalaz._

import play.api.mvc._
import play.api.libs.concurrent._

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.domain.Adapter._
import dev.example.eventsourcing.web._

import support._

object Application extends Controller with JaxbSupport {
  import global.Global.appserver._

  val paths = "dev.example.eventsourcing.domain:dev.example.eventsourcing.web"
  val config = JSONConfiguration.mapped().rootUnwrapping(false).build()

  // can be used for JAXB-based JSON and XML processing
  implicit val jsonJaxbContext = new JSONJAXBContext(config, paths)

  def index = Action {
    Ok(views.html.index("Work in progress ..."))
  }

  def invoices = Action { implicit request =>
    Ok(Jaxb(Invoices(invoiceService.getInvoices)))
  }

  def invoice(id: String) = Action { implicit request =>
    invoiceService.getInvoice(id) match {
      case None      => NotFound(Jaxb(SysError.NotFound))
      case Some(inv) => Ok(Jaxb(inv))
    }
  }

  def invoiceItems(id: String) = Action { implicit request =>
    invoiceService.getInvoice(id) match {
      case None      => NotFound(Jaxb(SysError.NotFound))
      case Some(inv) => Ok(Jaxb(InvoiceItems(inv.items)))
    }
  }

  def addInvoice = Action(jaxb.parse[DraftInvoice]) { implicit request => Async {
    invoiceService.createInvoice(request.body.id).asPromise.map { _ match {
      case Failure(err) => Conflict(Jaxb(AppError(err)))
      case Success(inv) => Created(Jaxb(Invoices(invoiceService.getInvoices)))
        .withHeaders(LOCATION -> location(request.uri, request.body.id))
    }}
  }}

  def addInvoiceItem(id: String) = Action(jaxb.parse[InvoiceItemVersioned]) { implicit request => Async {
    val versionOption = request.body.invoiceVersionOption
    val invoiceItem = request.body.toInvoiceItem
    invoiceService.getInvoice(id) match {
      case None      => Promise.pure(NotFound(Jaxb(SysError.NotFound)))
      case Some(inv) => invoiceService.addInvoiceItem(id, versionOption, invoiceItem).asPromise.map { _ match {
        case Failure(err) => Conflict(Jaxb(AppError(err)))
        case Success(upd) => Created(Jaxb(InvoiceItems(upd.items)))
          .withHeaders(LOCATION -> location(request.uri, lastItemPath(upd)))
      }}
    }
  }}

  private def lastItemPath(invoice: Invoice): String = "%s" format invoice.items.length - 1
}
