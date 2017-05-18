/*
 * Copyright 2017 47 Degrees, LLC. <http://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cards.nine.googleplay.service.free.interpreter.webscrapper

import cats.~>
import cards.nine.domain.application.{FullCard, Package}
import cards.nine.googleplay.domain.webscrapper.Failure
import cards.nine.googleplay.service.free.algebra.WebScraper._

trait InterpreterServer[F[_]] {
  def existsApp(pack: Package): F[Boolean]
  def getDetails(pack: Package): F[Failure Either FullCard]
}

case class MockInterpreter[F[_]](server: InterpreterServer[F]) extends (Ops ~> F) {

  override def apply[A](ops: Ops[A]) = ops match {
    case ExistsApp(pack)  ⇒ server.existsApp(pack)
    case GetDetails(pack) ⇒ server.getDetails(pack)
  }

}
