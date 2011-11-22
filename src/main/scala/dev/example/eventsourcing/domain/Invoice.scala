package dev.example.eventsourcing.domain

import javax.xml.bind.annotation._
import javax.xml.bind.annotation.adapters._

import dev.example.eventsourcing.util.Binding._

@XmlAccessorType(XmlAccessType.NONE)
case class Invoice(
    id: String,
    items: List[InvoiceItem] = Nil,
    discount: Option[BigDecimal] = None,
    sentTo: Option[InvoiceAddress] = None) extends EventSourced[Invoice] {

  def this() = this(null)

  def addItem(item: InvoiceItem): Update[Invoice] =
    update(InvoiceItemAdded(id, item))

  def setDiscount(discount: BigDecimal): Update[Invoice] =
    if (sum <= 100) Update.reject(DomainError("discount only on orders with sum > 100"))
    else            update(InvoiceDiscountSet(id, discount))

  def sendTo(address: InvoiceAddress): Update[Invoice] =
    if (items.isEmpty) Update.reject(DomainError("cannot send empty invoice"))
    else               update(InvoiceSent(id, address))

  def handle(event: Event) = event match {
    case InvoiceItemAdded(_, item)       => copy(items = item :: items)
    case InvoiceDiscountSet(_, discount) => copy(discount = Some(discount))
    case InvoiceSent(_, to)              => copy(sentTo = Some(to))
  }

  def total: BigDecimal = discount map (_ + sum) getOrElse sum
  def sum: BigDecimal = items.foldLeft(BigDecimal(0)) {
    (sum, item) => sum + item.amount * item.count
  }

  @XmlElement(required = true)
  @XmlJavaTypeAdapter(classOf[BigDecimalAdapter])
  def getTotal = total
}

object Invoice extends EventSourced[Invoice] {
  def create(id: String): Update[Invoice] = update(InvoiceCreated(id))
  def handle(event: Event) = event match {
    case event @ InvoiceCreated(invoiceId: String) => new Invoice(invoiceId)
  }
}

case class InvoiceItem(description: String, count: Int, amount: BigDecimal) {
  def this() = this(null, 0, 0)
}

case class InvoiceAddress(street: String, city: String, state: String) {
  def this() = this(null, null, null)
}

sealed trait InvoiceEvent extends Event {
  def invoiceId: String
}

case class InvoiceCreated(invoiceId: String) extends InvoiceEvent
case class InvoiceItemAdded(invoiceId: String, item: InvoiceItem) extends InvoiceEvent
case class InvoiceDiscountSet(invoiceId: String, discount: BigDecimal) extends InvoiceEvent
case class InvoiceSent(invoiceId: String, to: InvoiceAddress) extends InvoiceEvent
