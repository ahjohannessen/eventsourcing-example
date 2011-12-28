package dev.example.eventsourcing.domain

import java.util.{List => JList}
import javax.xml.bind.annotation._

import dev.example.eventsourcing.util.Binding._

object Adapter {
  class InvoiceItemsAdapter extends AbstractListAdapter[InvoiceItem, InvoiceItems] {
    def create(l: JList[InvoiceItem]) = new InvoiceItems(l)
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  case class InvoiceItems(@xmlElement(name = "item") elem: JList[InvoiceItem]) extends AbstractList[InvoiceItem] {
    private def this() = this(null)
  }

  @XmlRootElement
  @XmlAccessorType(XmlAccessType.FIELD)
  case class Invoices(@xmlElementRef(name = "invoices") elem: JList[Invoice]) extends AbstractList[Invoice] {
    private def this() = this(null)
  }
}