package dev.example.eventsourcing.domain

import dev.example.eventsourcing.event.Event

sealed trait Invoice extends Aggregate[Invoice] with Handler[InvoiceEvent, Invoice] {
  def items: List[InvoiceItem]
  def discount: BigDecimal

  def total: BigDecimal = sum - discount
  def sum: BigDecimal = items.foldLeft(BigDecimal(0)) {
    (sum, item) => sum + item.amount * item.count
  }
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

case class DraftInvoice(
    id: String,
    version: Long = 0,
    items: List[InvoiceItem] = Nil,
    discount: BigDecimal = 0)
  extends Invoice {

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

case class SentInvoice(
    id: String,
    version: Long = 0,
    items: List[InvoiceItem] = Nil,
    discount: BigDecimal = 0,
    address: InvoiceAddress)
  extends Invoice {

  def pay(amount: BigDecimal): Update[InvoiceEvent, PaidInvoice] =
    if (amount < total) Update.reject(DomainError("paid amount less than total amount"))
    else                update(InvoicePaid(id), transitionToPaid)

  def handle: PartialFunction[InvoiceEvent, Invoice] =
    transitionToPaid

  private def transitionToPaid: PartialFunction[InvoiceEvent, PaidInvoice] = {
    case InvoicePaid(id) => PaidInvoice(id, version + 1, items, discount, address)
  }
}

case class PaidInvoice(
    id: String,
    version: Long = 0,
    items: List[InvoiceItem] = Nil,
    discount: BigDecimal = 0,
    address: InvoiceAddress)
  extends Invoice {

  def paid = true
  def handle = throw new MatchError
}

case class InvoiceItem(description: String, count: Int, amount: BigDecimal)
case class InvoiceAddress(street: String, city: String, country: String)

sealed trait InvoiceEvent extends Event {
  def invoiceId: String
}

case class InvoiceCreated(invoiceId: String) extends InvoiceEvent
case class InvoiceItemAdded(invoiceId: String, item: InvoiceItem) extends InvoiceEvent
case class InvoiceDiscountSet(invoiceId: String, discount: BigDecimal) extends InvoiceEvent
case class InvoiceSent(invoiceId: String, invoice: Invoice, to: InvoiceAddress) extends InvoiceEvent
case class InvoicePaid(invoiceId: String) extends InvoiceEvent
