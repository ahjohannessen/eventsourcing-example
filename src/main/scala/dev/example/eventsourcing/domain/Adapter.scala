package dev.example.eventsourcing.domain

import java.util.{List => JList}
import javax.xml.bind.annotation._

import dev.example.eventsourcing.util.Binding._

object Adapter {
  class InvoiceItemsAdapter extends AbstractListAdapter[InvoiceItem, InvoiceItems] {
    def create(l: JList[InvoiceItem]) = new InvoiceItems(l)
  }

  @XmlRootElement(name = "items")
  @XmlAccessorType(XmlAccessType.FIELD)
  case class InvoiceItems(@xmlElementRef(name = "items") elem: JList[InvoiceItem]) extends AbstractList[InvoiceItem] {
    private def this() = this(null)
  }

  @XmlRootElement(name = "invoices")
  @XmlAccessorType(XmlAccessType.FIELD)
  case class Invoices(@xmlElementRef(name = "invoices") elem: JList[Invoice]) extends AbstractList[Invoice] {
    private def this() = this(null)
  }
}