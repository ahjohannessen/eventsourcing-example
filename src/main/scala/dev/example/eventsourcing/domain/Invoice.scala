package dev.example.eventsourcing.domain

case class Invoice(
    id: String,
    version: Long = 0,
    items: List[InvoiceItem] = Nil,
    discount: Option[BigDecimal] = None,
    sentTo: Option[InvoiceAddress] = None) extends Aggregate[Invoice] with EventSourced[InvoiceEvent, Invoice] {

  def addItem(item: InvoiceItem): Update[Invoice] =
    update(InvoiceItemAdded(id, item))

  def setDiscount(discount: BigDecimal): Update[Invoice] =
    if (sum <= 100) Update.reject(DomainError("discount only on orders with sum > 100"))
    else            update(InvoiceDiscountSet(id, discount))

  def sendTo(address: InvoiceAddress): Update[Invoice] =
    if (items.isEmpty) Update.reject(DomainError("cannot send empty invoice"))
    else               update(InvoiceSent(id, address))

  def handle(event: InvoiceEvent): Invoice = event match {
    case InvoiceItemAdded(_, item)       => copy(version = version + 1, items = item :: items)
    case InvoiceDiscountSet(_, discount) => copy(version = version + 1, discount = Some(discount))
    case InvoiceSent(_, to)              => copy(version = version + 1, sentTo = Some(to))
  }

  def total: BigDecimal = discount map (_ + sum) getOrElse sum
  def sum: BigDecimal = items.foldLeft(BigDecimal(0)) {
    (sum, item) => sum + item.amount * item.count
  }
}

object Invoice extends EventSourced[InvoiceEvent, Invoice] {
  def create(id: String): Update[Invoice] = update(InvoiceCreated(id))

  def handle(event: InvoiceEvent): Invoice = event match {
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
case class InvoiceSent(invoiceId: String, to: InvoiceAddress) extends InvoiceEvent
