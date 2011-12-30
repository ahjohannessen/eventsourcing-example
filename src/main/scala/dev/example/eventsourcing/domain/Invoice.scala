package dev.example.eventsourcing.domain

import javax.xml.bind.annotation._
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter

import dev.example.eventsourcing.event.Event
import dev.example.eventsourcing.util.Binding._

import Adapter._

@XmlType(propOrder = Array("total", "sum"))
@XmlAccessorType(XmlAccessType.PROPERTY)
sealed abstract class Invoice extends Aggregate[Invoice] with Handler[InvoiceEvent, Invoice] {
  def items: List[InvoiceItem]
  def discount: BigDecimal

  def total: BigDecimal = sum - discount

  def sum: BigDecimal = items.foldLeft(BigDecimal(0)) {
    (sum, item) => sum + item.amount * item.count
  }

  @XmlElement
  @XmlJavaTypeAdapter(classOf[BigDecimalAdapter])
  def getTotal = total

  @XmlElement
  @XmlJavaTypeAdapter(classOf[BigDecimalAdapter])
  def getSum = sum
}

object Invoice extends Handler[InvoiceEvent, Invoice] {
  def create(id: String): Update[InvoiceEvent, DraftInvoice] =
    update(InvoiceCreated(id), transitionToDraft)

  def handle: PartialFunction[InvoiceEvent, Invoice] =
    transitionToDraft

  def handle(events: List[InvoiceEvent]): Invoice =
    events.drop(1).foldLeft(handle(events(0))) { (invoice, event) => invoice.handle(event) }

  private def transitionToDraft: PartialFunction[InvoiceEvent, DraftInvoice] = {
    case InvoiceCreated(id) => DraftInvoice(id)
  }
}

@XmlRootElement(name = "draft-invoice")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = Array("discount", "items"))
case class DraftInvoice(
    @xmlAttribute(required = true) id: String,
    @xmlAttribute(required = false) version: Long = 0,
    @xmlJavaTypeAdapter(classOf[InvoiceItemsAdapter]) items: List[InvoiceItem] = Nil,
    @xmlJavaTypeAdapter(classOf[BigDecimalAdapter]) discount: BigDecimal = 0)
  extends Invoice {
  private def this() = this(id = null) // needed by JAXB

  def addItem(item: InvoiceItem): Update[InvoiceEvent, DraftInvoice] =
    update(InvoiceItemAdded(id, item), transitionToDraft)

  def setDiscount(discount: BigDecimal): Update[InvoiceEvent, DraftInvoice] =
    if (sum <= 100) Update.reject(DomainError("discount only on orders with sum > 100"))
    else            update(InvoiceDiscountSet(id, discount), transitionToDraft)

  def sendTo(address: InvoiceAddress): Update[InvoiceEvent, SentInvoice] =
    if (items.isEmpty) Update.reject(DomainError("cannot send empty invoice"))
    else               update(InvoiceSent(id, this, address), transitionToSent)

  def handle: PartialFunction[InvoiceEvent, Invoice] =
    transitionToDraft orElse transitionToSent

  private def transitionToDraft: PartialFunction[InvoiceEvent, DraftInvoice] = {
    case InvoiceItemAdded(_, item)       => copy(version = version + 1, items = items :+ item)
    case InvoiceDiscountSet(_, discount) => copy(version = version + 1, discount = discount)
  }

  private def transitionToSent: PartialFunction[InvoiceEvent, SentInvoice] = {
    case InvoiceSent(id, _, address) => SentInvoice(id, version + 1, items, discount, address)
  }
}

@XmlRootElement(name = "sent-invoice")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = Array("discount", "items", "address"))
case class SentInvoice(
    @xmlAttribute(required = true) id: String,
    @xmlAttribute(required = false) version: Long = 0,
    @xmlJavaTypeAdapter(classOf[InvoiceItemsAdapter]) items: List[InvoiceItem] = Nil,
    @xmlJavaTypeAdapter(classOf[BigDecimalAdapter]) discount: BigDecimal = 0,
    @xmlElement(required = true) address: InvoiceAddress)
  extends Invoice {
  private def this() = this(id = null, address = null) // needed by JAXB

  def pay(amount: BigDecimal): Update[InvoiceEvent, PaidInvoice] =
    if (amount < total) Update.reject(DomainError("paid amount less than total amount"))
    else                update(InvoicePaid(id), transitionToPaid)

  def handle: PartialFunction[InvoiceEvent, Invoice] =
    transitionToPaid

  private def transitionToPaid: PartialFunction[InvoiceEvent, PaidInvoice] = {
    case InvoicePaid(id) => PaidInvoice(id, version + 1, items, discount, address)
  }
}

@XmlRootElement(name = "paid-invoice")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = Array("discount", "items", "address"))
case class PaidInvoice(
    @xmlAttribute(required = true) id: String,
    @xmlAttribute(required = false) version: Long = 0,
    @xmlJavaTypeAdapter(classOf[InvoiceItemsAdapter]) items: List[InvoiceItem] = Nil,
    @xmlJavaTypeAdapter(classOf[BigDecimalAdapter]) discount: BigDecimal = 0,
    @xmlElement(required = true) address: InvoiceAddress)
  extends Invoice {
  private def this() = this(id = null, address = null) // needed by JAXB

  def paid = true
  def handle = throw new MatchError
}

@XmlRootElement(name = "item")
@XmlAccessorType(XmlAccessType.FIELD)
case class InvoiceItem(
  @xmlElement(required = true) description: String,
  @xmlElement(required = true) count: Int,
  @xmlElement(required = true) @xmlJavaTypeAdapter(classOf[BigDecimalAdapter]) amount: BigDecimal) {
  private def this() = this(null, 0, 0)
}

@XmlElement
@XmlAccessorType(XmlAccessType.FIELD)
case class InvoiceAddress(
  @xmlElement(required = true) street: String,
  @xmlElement(required = true) city: String,
  @xmlElement(required = true) country: String) {
  private def this() = this(null, null, null)
}

sealed trait InvoiceEvent extends Event {
  def invoiceId: String
}

case class InvoiceCreated(invoiceId: String) extends InvoiceEvent
case class InvoiceItemAdded(invoiceId: String, item: InvoiceItem) extends InvoiceEvent
case class InvoiceDiscountSet(invoiceId: String, discount: BigDecimal) extends InvoiceEvent
case class InvoiceSent(invoiceId: String, invoice: Invoice, to: InvoiceAddress) extends InvoiceEvent
case class InvoicePaid(invoiceId: String) extends InvoiceEvent

