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
import dev.example.eventsourcing.domain.Adapter._
import dev.example.eventsourcing.service.InvoiceService

@Component
@Path("/invoice")
class InvoicesResource {
  @Resource
  var service: InvoiceService = _

  @GET
  @Produces(Array(TEXT_HTML))
  def invoicesToHtml = new Viewable(webPath("Invoices.index"), InvoicesInfo(service.getInvoices))

  @GET
  @Produces(Array(TEXT_XML, APPLICATION_XML, APPLICATION_JSON))
  def invoicesToXmlJson = new Invoices(service.getInvoices.toList.asJava)

  @Path("{id}")
  def invoice(@PathParam("id") id: String) = new InvoiceResource(service.getInvoice(id), service)
}

class InvoiceResource(invoiceOption: Option[Invoice], service: InvoiceService) {
  @POST
  @Consumes(Array(APPLICATION_FORM_URLENCODED))
  @Produces(Array(TEXT_HTML))
  def updateInvoiceFromHtml(form: Form) = invoiceOption match {
    case None          => new Viewable(errorPath("404"))
    case Some(invoice) => {
      val addressForm = new InvoiceAddressForm(form.toMap)
      val validation = for {
        address <- addressForm.toInvoiceAddress
        updated <- service.sendInvoiceTo(invoice.id, Some(addressForm.data("version").toLong), address).get
      } yield updated
      validation match {
        case Success(si) => new Viewable(webPath("Invoice.sent"), InvoiceInfo(Some(si)))
        case Failure(er) => service.getInvoice(invoice.id) match {
          case None            => { // concurrent deletion
            val entity = new Viewable(errorPath("404"))
            Response.status(NOT_FOUND).entity(entity).build()
          }
          case Some(refreshed) => {
            val entity = new Viewable(webPath("Invoice.draft"), InvoiceInfo(service.getInvoice(invoice.id), Some(er), Some(addressForm.data)))
            Response.status(CONFLICT).entity(entity).build()
          }
        }
      }
    }
  }

  @PUT
  @Consumes(Array(TEXT_XML, APPLICATION_XML, APPLICATION_JSON))
  @Produces(Array(TEXT_XML, APPLICATION_XML, APPLICATION_JSON))
  def updateInvoiceFromXmlJson(sentInvoice: SentInvoice) = invoiceOption match {
    case None          => Response.status(NOT_FOUND).entity(SysError.NotFound).build()
    case Some(invoice) => service.sendInvoiceTo(invoice.id, sentInvoice.versionOption, sentInvoice.address).get match {
      case Success(si) => Response.ok(si).build()
      case Failure(er) => service.getInvoice(invoice.id) match {
        case None            => Response.status(NOT_FOUND).entity(SysError.NotFound).build() // concurrent deletion
        case Some(refreshed) => Response.status(CONFLICT).entity(AppError(er)).build()
      }
    }
  }

  @GET
  @Produces(Array(TEXT_HTML))
  def invoiceToHtml = invoiceOption match {
    case None          => new Viewable(errorPath("404"))
    case Some(invoice) => invoice match {
      case di: DraftInvoice => new Viewable(webPath("Invoice.draft"), InvoiceInfo(Some(di)))
      case si: SentInvoice  => new Viewable(webPath("Invoice.sent"), InvoiceInfo(Some(si)))
      case pi: PaidInvoice  => new Viewable(webPath("Invoice.paid"), InvoiceInfo(Some(pi)))
    }
  }

  @GET
  @Produces(Array(TEXT_XML, APPLICATION_XML, APPLICATION_JSON))
  def invoiceToXmlJson = invoiceOption match {
    case None          => Response.status(NOT_FOUND).entity(SysError.NotFound).build()
    case Some(invoice) => Response.ok(invoice).build()
  }

  @Path("item")
  def items = new InvoiceItemsResource(invoiceOption, service, this)
}

class InvoiceItemsResource(invoiceOption: Option[Invoice], service: InvoiceService, parent: InvoiceResource) {
  @POST
  @Consumes(Array(APPLICATION_FORM_URLENCODED))
  @Produces(Array(TEXT_HTML))
  def updateInvoiceItemsFromHtml(form: Form) = invoiceOption match {
    case None          => new Viewable(errorPath("404"))
    case Some(invoice) => {
      val itemForm = new InvoiceItemForm(form.toMap)
      val validation = for {
        item    <- itemForm.toInvoiceItem
        updated <- service.addInvoiceItem(invoice.id, Some(itemForm.data("version").toLong), item).get
      } yield updated
      validation match {
        case Success(di) => {
          val entity = new Viewable(webPath("Invoice.draft"), InvoiceInfo(Some(di)))
          Response.created(uri("/invoice/%s/item/%s" format (di.id, di.items.length - 1))).entity(entity).build()
        }
        case Failure(er) => service.getInvoice(invoice.id) match {
          case None  => {  // concurrent deletion
            val entity = new Viewable(errorPath("404"))
            Response.status(NOT_FOUND).entity(entity).build()
          }
          case Some(refreshed) => {
            val entity = new Viewable(webPath("Invoice.draft"), InvoiceInfo(service.getInvoice(invoice.id), Some(er), Some(itemForm.data)))
            Response.status(CONFLICT).entity(entity).build()
          }
        }
      }
    }
  }

  @POST
  @Consumes(Array(TEXT_XML, APPLICATION_XML, APPLICATION_JSON))
  @Produces(Array(TEXT_XML, APPLICATION_XML, APPLICATION_JSON))
  def updateInvoiceItemsFromXmlJson(itemv: InvoiceItemVersioned) = invoiceOption match {
    case None          => Response.status(NOT_FOUND).entity(SysError.NotFound).build()
    case Some(invoice) => service.addInvoiceItem(invoice.id, itemv.invoiceVersionOption, itemv.toInvoiceItem).get match {
      case Success(di) => Response.created(uri("/invoice/%s/item/%s" format (di.id, di.items.length - 1))).entity(itemv.toInvoiceItem).build()
      case Failure(er) => service.getInvoice(invoice.id) match {
        case None            => Response.status(NOT_FOUND).entity(SysError.NotFound).build() // concurrent deletion
        case Some(refreshed) => Response.status(CONFLICT).entity(AppError(er)).build()
      }
    }
  }

  @GET
  @Produces(Array(TEXT_HTML))
  def invoiceItemsToHtml = parent.invoiceToHtml

  @GET
  @Produces(Array(TEXT_XML, APPLICATION_XML, APPLICATION_JSON))
  def invoiceItemsToXmlJson = invoiceOption match {
    case None          => Response.status(NOT_FOUND).entity(SysError.NotFound).build()
    case Some(invoice) => new InvoiceItems(invoice.items.asJava)
  }

  @Path("{index}")
  def invoice(@PathParam("index") index: Int) = {
    val invoiceItemOption = for {
      invoice     <- invoiceOption
      invoiceItem <- item(invoice, index)
    } yield invoiceItem
    new InvoiceItemResource(invoiceOption, invoiceItemOption, service, this)
  }

  def item(invoice: Invoice, index: Int): Option[InvoiceItem] =
    if (index < invoice.items.length) Some(invoice.items(index)) else None
}

class InvoiceItemResource(invoiceOption: Option[Invoice], invoiceItemOption: Option[InvoiceItem], service: InvoiceService, parent: InvoiceItemsResource) {
  @GET
  @Produces(Array(TEXT_HTML))
  def invoiceToHtml = invoiceItemOption match {
    case None    => new Viewable(errorPath("404"))
    case Some(_) => parent.invoiceItemsToHtml
  }

  @GET
  @Produces(Array(TEXT_XML, APPLICATION_XML, APPLICATION_JSON))
  def invoiceToXmlJson = invoiceItemOption match {
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

case class InvoiceInfo[A <: Invoice](
  invoiceOption: Option[A],
  errorsOption:  Option[DomainError] = None,
  formOption:    Option[Map[String, String]] = None) {

  def uncommitted(key: String) = formOption.map(_.get(key)) match {
    case Some(Some(value)) => value
    case _                 => ""
  }
}

private[web] class InvoiceIdForm(val data: Map[String, String]) {
  def toInvoiceId: DomainValidation[String] = data("id") match {
    case "" => Failure(DomainError("id must not be empty"))
    case id => Success(id)
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