package dev.example.eventsourcing.domain

import scalaz._

trait Update[+E, +A] {
  def apply[EE >: E](events: List[EE] = Nil): (List[EE], DomainValidation[A])

  def map[B](f: A => B) = Update[E, B] { events =>
    this(events) match {
      case (updatedEvents, Success(result)) => (updatedEvents, Success(f(result)))
      case (updatedEvents, Failure(errors)) => (updatedEvents, Failure(errors))
    }
  }

  def flatMap[EE >: E, B](f: A => Update[EE, B]) = Update[EE, B] { events =>
    this(events) match {
      case (updatedEvents, Success(result)) => f(result)(updatedEvents)
      case (updatedEvents, Failure(errors)) => (updatedEvents, Failure(errors))
    }
  }

  def result(onSuccess: (List[E], A) => Unit = (e, r) => ()): DomainValidation[A] = {
    val (events, validation) = apply()
    validation match {
      case Success(result) => { onSuccess(events, result); Success(result) }
      case failure         => failure
    }
  }
}

object Update {
  def apply[E, A](f: List[E] => (List[E], DomainValidation[A])) = new Update[E, A] {
    def apply[EE >: E](events: List[EE]) = f(events.asInstanceOf[List[E]])
  }

  def accept[E, A](result: A) =
    Update[E, A](events => (events, Success(result)))

  def accept[E, A](event: E, result: A) =
    Update[E, A](events => (event :: events, Success(result)))

  def reject[E, A](errors: DomainError) =
    Update[E, A](events => (events, Failure(errors)))
}
