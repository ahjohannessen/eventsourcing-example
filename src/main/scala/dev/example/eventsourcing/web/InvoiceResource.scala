package dev.example.eventsourcing.web

import javax.annotation.Resource
import javax.ws.rs._
import javax.ws.rs.core.MediaType._
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status._

import scala.collection.JavaConverters._

import scalaz._
import Scalaz._

import com.sun.jersey.api.representation.Form
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
  @POST
  @Consumes(Array(APPLICATION_FORM_URLENCODED))
  @Produces(Array(TEXT_HTML))
  def updateInvoiceFromHtml(form: Form) = {
    val formData = form.toMap
    if (formData.contains("description")) updateInvoiceFromInvoiceItemForm(new InvoiceItemForm(formData))
    else                                  updateInvoiceFromInvoiceAddressForm(new InvoiceAddressForm(formData))
  }

  @GET
  @Produces(Array(TEXT_HTML))
  def encounterToHtml = service.getInvoice(invoiceId) match {
    case None          => new Viewable(errorPath("404"), invoiceId)
    case Some(invoice) => invoice match {
      case di: DraftInvoice => new Viewable(webPath("Invoice.draft"), InvoiceInfo(Some(di)))
      case si: SentInvoice  => new Viewable(webPath("Invoice.sent"), InvoiceInfo(Some(si)))
      case pi: PaidInvoice  => new Viewable(webPath("Invoice.paid"), InvoiceInfo(Some(pi)))
    }
  }

  @GET
  @Produces(Array(TEXT_XML, APPLICATION_XML, APPLICATION_JSON))
  def invoiceToXmlJson = service.getInvoice(invoiceId) match {
    case None          => Response.status(NOT_FOUND).entity(SysError.NotFound).build()
    case Some(invoice) => Response.ok(invoice).build()
  }

  private def updateInvoiceFromInvoiceItemForm(form: InvoiceItemForm) = {
    val validation = for {
      invoiceItem <- form.toInvoiceItem
      invoice     <- service.addInvoiceItem(invoiceId, Some(form.data("version").toLong), invoiceItem).get
    } yield invoice
    validation match {
      case Success(di) => new Viewable(webPath("Invoice.draft"), InvoiceInfo(Some(di))) // alternative: redirect
      case Failure(er) => new Viewable(webPath("Invoice.draft"), InvoiceInfo(service.getInvoice(invoiceId), Some(er), Some(form.data)))
    }
  }

  private def updateInvoiceFromInvoiceAddressForm(form: InvoiceAddressForm) = {
    val validation = for {
      invoiceAddress <- form.toInvoiceAddress
      invoice        <- service.sendInvoiceTo(invoiceId, Some(form.data("version").toLong), invoiceAddress).get
    } yield invoice
    validation match {
      case Success(di) => new Viewable(webPath("Invoice.sent"), InvoiceInfo(Some(di))) // alternative: redirect
      case Failure(er) => new Viewable(webPath("Invoice.draft"), InvoiceInfo(service.getInvoice(invoiceId), Some(er), Some(form.data)))
    }
  }
}

case class InvoicesInfo(invoices: Iterable[Invoice]) {
  def status(invoice: Invoice) = invoice match {
    case _: DraftInvoice => "draft"
    case _: SentInvoice  => "sent"
    case _: PaidInvoice  => "paid"
  }
}

case class InvoiceInfo[A <: Invoice](
  invoiceOption: Option[A],
  errorsOption:  Option[DomainError] = None,
  formOption:    Option[Map[String, String]] = None) {

  def uncommitted(key: String) = formOption.map(_.get(key)) match {
    case Some(Some(value)) => value
    case _                 => ""
  }
}

private[web] class InvoiceItemForm(val data: Map[String, String]) {
  def toInvoiceItem: DomainValidation[InvoiceItem] =
    (description ⊛ count ⊛ amount) (InvoiceItem.apply)

  private def description: DomainValidation[String] = data("description") match {
    case "" => Failure(DomainError("description must not be empty"))
    case d  => Success(d)
  }

  private def count: DomainValidation[Int] = data("count") match {
    case "" => Failure(DomainError("count must not be empty"))
    case c  => try {
      Success(c.toInt)
    } catch {
      case e => Failure(DomainError("count must be an int"))
    }
  }

  private def amount: DomainValidation[BigDecimal] = data("amount") match {
    case "" => Failure(DomainError("amount must not be empty"))
    case a  => try {
      Success(BigDecimal(a))
    } catch {
      case e => Failure(DomainError("amount must be a number"))
    }
  }
}

private[web] class InvoiceAddressForm(val data: Map[String, String]) {
  def toInvoiceAddress: DomainValidation[InvoiceAddress] =
    (street ⊛ city ⊛ country) (InvoiceAddress.apply)

  private def street: DomainValidation[String] = data("street") match {
    case "" => Failure(DomainError("street must not be empty"))
    case s  => Success(s)
  }

  private def city: DomainValidation[String] = data("city") match {
    case "" => Failure(DomainError("city must not be empty"))
    case c  => Success(c)
  }

  private def country: DomainValidation[String] = data("country") match {
    case "" => Failure(DomainError("country must not be empty"))
    case c  => Success(c)
  }
}