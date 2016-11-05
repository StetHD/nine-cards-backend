package cards.nine.api.utils

import cards.nine.api.NineCardsErrorHandler
import cards.nine.commons.NineCardsService.Result
import cards.nine.processes.App._
import cats.data.Xor
import cats.free.Free
import cats.{ Monad, RecursiveTailRecM }
import io.freestyle.syntax._
import shapeless.Lazy
import spray.httpx.marshalling.ToResponseMarshaller

import scalaz.concurrent.Task

object SprayMarshallers {

  implicit def ninecardsResultMarshaller[A](
    implicit
    m: ToResponseMarshaller[A],
    handler: NineCardsErrorHandler
  ): ToResponseMarshaller[Result[A]] =
    ToResponseMarshaller[Result[A]] {
      (result, ctx) ⇒
        result.fold(
          left ⇒ handler.handleNineCardsErrors(left, ctx),
          right ⇒ m(right, ctx)
        )
    }

  implicit def catsXorMarshaller[T <: Throwable, A](
    implicit
    m: ToResponseMarshaller[A]
  ): ToResponseMarshaller[Xor[T, A]] =
    ToResponseMarshaller[Xor[T, A]] {
      (xor, ctx) ⇒
        xor.fold(
          left ⇒ ctx.handleError(left),
          right ⇒ m(right, ctx)
        )
    }

  implicit def tasksMarshaller[A](
    implicit
    m: ToResponseMarshaller[A]
  ): ToResponseMarshaller[Task[A]] =
    ToResponseMarshaller[Task[A]] {
      (task, ctx) ⇒
        task.unsafePerformAsync {
          _.fold(
            left ⇒ ctx.handleError(left),
            right ⇒ m(right, ctx)
          )
        }
    }

  implicit def freeTaskMarshaller[A](
    implicit
    taskMarshaller: Lazy[ToResponseMarshaller[Task[A]]],
    taskMonad: Monad[Task],
    taskRecTail: RecursiveTailRecM[Task]
  ): ToResponseMarshaller[Free[NineCardsApp.T, A]] =
    ToResponseMarshaller[Free[NineCardsApp.T, A]] {
      (free, ctx) ⇒
        taskMarshaller.value(free.exec[Task], ctx)
    }
}
