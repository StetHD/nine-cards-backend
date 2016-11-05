package cards.nine.processes

import cards.nine.services.free.algebra._
import cards.nine.services.free.interpreter.Interpreters
import cats._

import scala.util.{ Failure, Success, Try }
import scalaz.concurrent.Task

trait IdInstances {
  implicit def idApplicativeError(
    implicit
    I: Applicative[cats.Id]
  ): ApplicativeError[cats.Id, Throwable] =
    new ApplicativeError[Id, Throwable] {

      override def pure[A](x: A): Id[A] = I.pure(x)

      override def ap[A, B](ff: Id[A ⇒ B])(fa: Id[A]): Id[B] = I.ap(ff)(fa)

      override def map[A, B](fa: Id[A])(f: Id[A ⇒ B]): Id[B] = I.map(fa)(f)

      override def product[A, B](fa: Id[A], fb: Id[B]): Id[(A, B)] = I.product(fa, fb)

      override def raiseError[A](e: Throwable): Id[A] = throw e

      override def handleErrorWith[A](fa: Id[A])(f: Throwable ⇒ Id[A]): Id[A] =
        Try(fa) match {
          case Success(v) ⇒ v
          case Failure(e) ⇒ f(e)
        }
    }
}

trait TestInterpreters extends IdInstances {

  val taskToId: Task ~> Id = new (Task ~> Id) {
    def apply[A](fa: Task[A]): Id[A] = fa.unsafePerformSync
  }

  implicit val analyticsInterpreter: GoogleAnalytics.Services.T ~> Id =
    Interpreters.analyticsInterpreter.andThen(taskToId)

  implicit val collectionInterpreter: SharedCollection.Services.T ~> Id =
    Interpreters.collectionInterpreter.andThen(taskToId)

  implicit val countryInterpreter: Country.Services.T ~> Id =
    Interpreters.countryInterpreter.andThen(taskToId)

  implicit val firebaseInterpreter: Firebase.Services.T ~> Id =
    Interpreters.firebaseInterpreter.andThen(taskToId)

  implicit val googleApiInterpreter: GoogleApi.Services.T ~> Id =
    Interpreters.googleApiInterpreter.andThen(taskToId)

  implicit val googlePlayInterpreter: GooglePlay.Services.T ~> Id =
    Interpreters.googlePlayInterpreter.andThen(taskToId)

  implicit val rankingInterpreter: Ranking.Services.T ~> Id =
    Interpreters.rankingInterpreter.andThen(taskToId)

  implicit val subscriptionInterpreter: Subscription.Services.T ~> Id =
    Interpreters.subscriptionInterpreter.andThen(taskToId)

  implicit val userInterpreter: User.Services.T ~> Id =
    Interpreters.userInterpreter.andThen(taskToId)
}
