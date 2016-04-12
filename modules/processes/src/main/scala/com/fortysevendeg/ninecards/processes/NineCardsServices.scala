package com.fortysevendeg.ninecards.processes

import cats.data.Coproduct
import cats.{Monad, ~>}
import com.fortysevendeg.ninecards.services.free.algebra.DBResult.DBResult
import com.fortysevendeg.ninecards.services.free.algebra.GoogleApiServices.GoogleApiOps
import com.fortysevendeg.ninecards.services.free.interpreter.Interpreters._
import com.fortysevendeg.ninecards.services.free.interpreter.Interpreters.interpreters._

import scalaz.concurrent.Task

object NineCardsServices {

  type NineCardsServices[A] = Coproduct[DBResult, GoogleApiOps, A]

  implicit val taskMonadInstance: Monad[Task] = taskMonad

  val interpreters: NineCardsServices ~> Task = DBResultInterpreter or GoogleAPIServicesInterpreter
}