package dev.example.eventsourcing.web

import javax.annotation.Resource
import javax.ws.rs._
import javax.ws.rs.core.MediaType._
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status._

import scala.collection.JavaConverters._

import org.springframework.stereotype.Component

import dev.example.eventsourcing.domain.Adapter.Invoices
import dev.example.eventsourcing.service.InvoiceService

@Component
@Path("/invoice")
class InvoicesResource {
  @Resource
  var service: InvoiceService = _

  @GET
  @Produces(Array(TEXT_XML, APPLICATION_XML, APPLICATION_JSON))
  def invoicesToXmlJson = new Invoices(service.getInvoices.toList.asJava)

  @Path("{id}")
  def invoice(@PathParam("id") id: String) = new InvoiceResource(id, service)
}

class InvoiceResource(val invoiceId: String, service: InvoiceService) {
  @GET
  @Produces(Array(TEXT_XML, APPLICATION_XML, APPLICATION_JSON))
  def invoiceToXmlJson = service.getInvoice(invoiceId) match {
    case Some(encounter) => Response.ok(encounter).build()
    case None            => Response.status(NOT_FOUND).entity(SysError.NotFound).build()
  }


}