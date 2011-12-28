package dev.example.eventsourcing.web

import javax.annotation.Resource
import javax.ws.rs._
import javax.ws.rs.core.MediaType._
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status._

import scala.collection.JavaConverters._

import com.sun.jersey.api.view.Viewable

import org.springframework.stereotype.Component

import dev.example.eventsourcing.domain._
import dev.example.eventsourcing.domain.Adapter.Invoices
import dev.example.eventsourcing.service.InvoiceService

@Component
@Path("/invoice")
class InvoicesResource {
  @Resource
  var service: InvoiceService = _

  @GET
  @Produces(Array(TEXT_HTML))
  def encountersToHtml = new Viewable(webPath("Invoices.index"), InvoicesInfo(service.getInvoices))

  @GET
  @Produces(Array(TEXT_XML, APPLICATION_XML, APPLICATION_JSON))
  def invoicesToXmlJson = new Invoices(service.getInvoices.toList.asJava)

  @Path("{id}")
  def invoice(@PathParam("id") id: String) = new InvoiceResource(id, service)
}

class InvoiceResource(val invoiceId: String, service: InvoiceService) {
  @GET
  @Produces(Array(TEXT_HTML))
  def encounterAsHtml = service.getInvoice(invoiceId) match {
    case None          => new Viewable(errorPath("404"), invoiceId)
    case Some(invoice) => invoice match {
      case di: DraftInvoice => new Viewable(webPath("Invoice.draft"), InvoiceInfo(di))
      case si: SentInvoice  => new Viewable(webPath("Invoice.sent"), InvoiceInfo(si))
      case pi: PaidInvoice  => new Viewable(webPath("Invoice.paid"), InvoiceInfo(pi))
    }
  }

  @GET
  @Produces(Array(TEXT_XML, APPLICATION_XML, APPLICATION_JSON))
  def invoiceToXmlJson = service.getInvoice(invoiceId) match {
    case None          => Response.status(NOT_FOUND).entity(SysError.NotFound).build()
    case Some(invoice) => Response.ok(invoice).build()
  }
}

case class InvoicesInfo(invoices: Iterable[Invoice]) {
  def status(invoice: Invoice) = invoice match {
    case _: DraftInvoice => "draft"
    case _: SentInvoice  => "sent"
    case _: PaidInvoice  => "paid"
  }
}

case class InvoiceInfo[A <: Invoice](invoice: A) {
  // ...
}
