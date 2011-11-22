package dev.example.eventsourcing.util

import java.math.{BigDecimal => JBigDecimal}

import scala.annotation.target.field

import javax.xml.bind.annotation._
import javax.xml.bind.annotation.adapters.{XmlAdapter, XmlJavaTypeAdapter}

object Binding {
  type xmlAnyElement      = XmlAnyElement @field
  type xmlElement         = XmlElement @field
  type xmlElementRef      = XmlElementRef @field
  type xmlElementRefs     = XmlElementRefs @field
  type xmlElementWrapper  = XmlElementWrapper @field
  type xmlJavaTypeAdapter = XmlJavaTypeAdapter @field
  type xmlTransient       = XmlTransient @field

  class BigDecimalAdapter extends XmlAdapter[JBigDecimal, BigDecimal] {
    import BigDecimal.javaBigDecimal2bigDecimal
    def unmarshal(v: JBigDecimal): BigDecimal = v // implicit conversion
    def marshal(v: BigDecimal): JBigDecimal = v.underlying
  }
}