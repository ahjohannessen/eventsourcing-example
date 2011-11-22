package dev.example.eventsourcing

import scalaz.Validation

package object domain {
  type DomainValidation[+α] = ({type λ[α]=Validation[DomainError, α]})#λ[α]
  type DomainError          = List[String]

  object DomainError {
    def apply(msg: String): DomainError = List(msg)
  }
}