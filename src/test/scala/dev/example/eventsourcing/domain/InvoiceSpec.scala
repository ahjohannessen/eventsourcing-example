package dev.example.eventsourcing.domain

import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers

class InvoiceSpec extends WordSpec with MustMatchers {

  "An invoice" can {
    "be re-constructured from a history of events" in {
      val events = List(
        InvoiceCreated("test"),
        InvoiceItemAdded("test", InvoiceItem("a", 1, 1)),
        InvoiceItemAdded("test", InvoiceItem("b", 2, 2))
      )
      Invoice.handle(events) must be(Invoice("test", 2, List(InvoiceItem("b", 2, 2), InvoiceItem("a", 1, 1))))
    }
  }
}