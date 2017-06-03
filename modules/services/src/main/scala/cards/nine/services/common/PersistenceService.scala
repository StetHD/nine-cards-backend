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
package cards.nine.services.common

import cards.nine.commons.catscalaz.ScalazInstances
import cards.nine.commons.NineCardsErrors.NineCardsError
import cats.data.EitherT
import cats.syntax.either._
import doobie.imports._

object PersistenceService {
  type PersistenceService[A] = EitherT[ConnectionIO, NineCardsError, A]

  val instances: ScalazInstances[ConnectionIO] = ScalazInstances[ConnectionIO]

  import instances._

  def pure[A](value: A): PersistenceService[A] =
    EitherT.pure(value)(applicativeInstance)

  def apply[A](connectionIO: ConnectionIO[A]): PersistenceService[A] =
    EitherT.right(connectionIO)(instances.applicativeInstance)

  def fromOptionF[A](ciOpt: ConnectionIO[Option[A]], err: NineCardsError): PersistenceService[A] =
    EitherT(instances.monadInstance.map(ciOpt)(opt ⇒ Either.fromOption(opt, err)))

}
