package dev.example.eventsourcing.domain

import dev.example.eventsourcing.event.Event

case class Invoice(
    id: String,
    version: Long = 0,
    items: List[InvoiceItem] = Nil,
    discount: Option[BigDecimal] = None,
    sentTo: Option[InvoiceAddress] = None,
    paid: Boolean = false) extends Aggregate[InvoiceEvent, Invoice] with Handler[InvoiceEvent, Invoice] {

  def addItem(item: InvoiceItem): Update[InvoiceEvent, Invoice] =
    update(InvoiceItemAdded(id, item))

  def setDiscount(discount: BigDecimal): Update[InvoiceEvent, Invoice] =
    if (sum <= 100) Update.reject(DomainError("discount only on orders with sum > 100"))
    else            update(InvoiceDiscountSet(id, discount))

  def sendTo(address: InvoiceAddress): Update[InvoiceEvent, Invoice] =
    if (items.isEmpty) Update.reject(DomainError("cannot send empty invoice"))
    else               update(InvoiceSent(id, this, address))

  def pay(amount: BigDecimal): Update[InvoiceEvent, Invoice] =
    if (amount < total) Update.reject(DomainError("paid amount less than total amount"))
    else                update(InvoicePaid(id))

  def handle(event: InvoiceEvent) = event match {
    case InvoiceItemAdded(_, item)       => copy(version = version + 1, items = items :+ item)
    case InvoiceDiscountSet(_, discount) => copy(version = version + 1, discount = Some(discount))
    case InvoiceSent(_, _, to)           => copy(version = version + 1, sentTo = Some(to))
    case InvoicePaid(_)                  => copy(version = version + 1, paid = true)
  }

  def total: BigDecimal = discount map (_ + sum) getOrElse sum
  def sum: BigDecimal = items.foldLeft(BigDecimal(0)) {
    (sum, item) => sum + item.amount * item.count
  }
}

object Invoice extends Handler[InvoiceEvent, Invoice] {
  def create(id: String): Update[InvoiceEvent, Invoice] = update(InvoiceCreated(id))

  def handle(event: InvoiceEvent) = event match {
    case event @ InvoiceCreated(invoiceId: String) => new Invoice(invoiceId)
  }

  def handle(events: List[InvoiceEvent]): Invoice = {
    events.drop(1).foldLeft(handle(events(0))) { (invoice, event) => invoice.handle(event) }
  }
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
