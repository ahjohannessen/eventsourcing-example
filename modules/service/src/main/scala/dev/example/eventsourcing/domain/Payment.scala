package dev.example.eventsourcing.domain

import dev.example.eventsourcing.event.Event

case class PaymentReceived(invoiceId: String, amount: BigDecimal) extends Event
