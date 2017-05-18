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

package cards.nine.services.free.algebra

import cards.nine.commons.NineCardsService
import cards.nine.commons.NineCardsService.{NineCardsService, Result}
import cards.nine.domain.application.Package
import cards.nine.domain.pagination.Page
import cards.nine.services.free.domain
import cards.nine.services.free.interpreter.collection.Services.SharedCollectionData
import cats.free.:<:

object SharedCollection {

  sealed trait Ops[A]

  case class Add(collection: SharedCollectionData) extends Ops[Result[domain.SharedCollection]]

  case class GetById(id: Long) extends Ops[Result[domain.SharedCollection]]

  case class GetByPublicId(publicId: String) extends Ops[Result[domain.SharedCollection]]

  case class GetByUser(user: Long)
      extends Ops[Result[List[domain.SharedCollectionWithAggregatedInfo]]]

  case class GetLatestByCategory(category: String, pageParams: Page)
      extends Ops[Result[List[domain.SharedCollection]]]

  case class GetTopByCategory(category: String, pageParams: Page)
      extends Ops[Result[List[domain.SharedCollection]]]

  case class IncreaseViewsByOne(id: Long) extends Ops[Result[Int]]

  case class Update(id: Long, title: String) extends Ops[Result[Int]]

  case class UpdatePackages(collection: Long, packages: List[Package])
      extends Ops[Result[(List[Package], List[Package])]]

  class Services[F[_]](implicit I: Ops :<: F) {

    def add(collection: SharedCollectionData): NineCardsService[F, domain.SharedCollection] =
      NineCardsService(Add(collection))

    def getById(id: Long): NineCardsService[F, domain.SharedCollection] =
      NineCardsService(GetById(id))

    def getByPublicId(publicId: String): NineCardsService[F, domain.SharedCollection] =
      NineCardsService(GetByPublicId(publicId))

    def getByUser(
        user: Long): NineCardsService[F, List[domain.SharedCollectionWithAggregatedInfo]] =
      NineCardsService(GetByUser(user))

    def getLatestByCategory(
        category: String,
        pageParams: Page): NineCardsService[F, List[domain.SharedCollection]] =
      NineCardsService(GetLatestByCategory(category, pageParams))

    def getTopByCategory(
        category: String,
        pageParams: Page): NineCardsService[F, List[domain.SharedCollection]] =
      NineCardsService(GetTopByCategory(category, pageParams))

    def increaseViewsByOne(id: Long): NineCardsService[F, Int] =
      NineCardsService(IncreaseViewsByOne(id))

    def update(id: Long, title: String): NineCardsService[F, Int] =
      NineCardsService(Update(id, title))

    def updatePackages(
        collection: Long,
        packages: List[Package]): NineCardsService[F, (List[Package], List[Package])] =
      NineCardsService(UpdatePackages(collection, packages))
  }

  object Services {

    implicit def services[F[_]](implicit I: Ops :<: F): Services[F] = new Services[F]

  }

}
