package dev.example.eventsourcing.util

import java.math.{BigDecimal => JBigDecimal}
import java.util.{ArrayList, List => JList}

import scala.annotation.target.field

import javax.xml.bind.annotation._
import javax.xml.bind.annotation.adapters._

object Binding {
  type xmlAnyElement      = XmlAnyElement @field
  type xmlAttribute       = XmlAttribute @field
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

  abstract class AbstractListAdapter[A, B <: AbstractList[A]] extends XmlAdapter[B, List[A]] {
    import scala.collection.JavaConverters._

    def marshal(v: List[A]) = if (v == null) create(new ArrayList[A]) else create(v.asJava)
    def unmarshal(v: B) = v.elem.asScala.toList
    def create(l: JList[A]): B
  }

  trait AbstractList[A] {
    def elem: JList[A]
  }
}