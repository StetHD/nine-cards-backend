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
package cards.nine.services.free.interpreter.collection

import cards.nine.commons.catscalaz.ScalazInstances
import cards.nine.commons.NineCardsService.Result
import cards.nine.commons.NineCardsErrors.SharedCollectionNotFound
import cards.nine.domain.application.Package
import cards.nine.domain.pagination.Page
import cards.nine.services.common.PersistenceService
import cards.nine.services.common.PersistenceService._
import cards.nine.services.free.algebra.CollectionR
import cards.nine.services.free.domain.SharedCollection.Queries
import cards.nine.services.free.domain._
import cards.nine.services.persistence.Persistence
import cats.Monad
import doobie.contrib.postgresql.pgtypes._
import doobie.imports.ConnectionIO
import shapeless.syntax.std.product._

class Services(
  collectionPersistence: Persistence[SharedCollection]
)(implicit connectionIOMonad: Monad[ConnectionIO]) extends CollectionR.Handler[ConnectionIO] {

  override def add(data: SharedCollectionData): ConnectionIO[Result[SharedCollection]] =
    PersistenceService.right(
      collectionPersistence.updateWithGeneratedKeys(
        sql    = Queries.insert,
        fields = SharedCollection.allFields,
        values = data.toTuple
      )
    ).value

  override def getById(id: Long): ConnectionIO[Result[SharedCollection]] = getByIdAux(id).value

  private[this] def getByIdAux(id: Long): PersistenceService[SharedCollection] =
    PersistenceService.fromOptionF(
      collectionPersistence.fetchOption(Queries.getById, id),
      SharedCollectionNotFound("Shared collection not found")
    )

  override def getByPublicId(publicIdentifier: String): ConnectionIO[Result[SharedCollection]] =
    PersistenceService.fromOptionF(
      collectionPersistence.fetchOption(
        sql    = Queries.getByPublicIdentifier,
        values = publicIdentifier
      ),
      SharedCollectionNotFound(s"Shared collection with public identifier $publicIdentifier doesn't exist")
    ).value

  override def getByUser(user: Long): ConnectionIO[Result[List[SharedCollectionWithAggregatedInfo]]] =
    PersistenceService.right(
      collectionPersistence.fetchListAs[SharedCollectionWithAggregatedInfo](
        sql    = Queries.getByUser,
        values = user
      )
    ).value

  override def getLatestByCategory(
    category: String, pageParams: Page
  ): ConnectionIO[Result[List[SharedCollection]]] =
    PersistenceService.right(
      collectionPersistence.fetchList(
        sql    = Queries.getLatestByCategory,
        values = (category, pageParams.pageSize, pageParams.pageNumber)
      )
    ).value

  override def getTopByCategory(category: String, pageParams: Page): ConnectionIO[Result[List[SharedCollection]]] =
    PersistenceService.right(
      collectionPersistence.fetchList(
        sql    = Queries.getTopByCategory,
        values = (category, pageParams.pageSize, pageParams.pageNumber)
      )
    ).value

  override def increaseViewsByOne(id: Long): ConnectionIO[Result[Int]] =
    PersistenceService.right(
      collectionPersistence.update(
        sql    = Queries.increaseViewsByOne,
        values = id
      )
    ).value

  override def update(id: Long, title: String): ConnectionIO[Result[Int]] =
    PersistenceService.right(
      collectionPersistence.update(
        sql    = Queries.update,
        values = (title, id)
      )
    ).value

  override def updatePackages(
    collectionId: Long, packages: List[Package]
  ): ConnectionIO[Result[(List[Package], List[Package])]] = {

    def updatePackagesInfo(newPackages: List[Package], removedPackages: List[Package]): PersistenceService[Int] =
      if (newPackages.nonEmpty || removedPackages.nonEmpty)
        PersistenceService.right(
          collectionPersistence
            .update(Queries.updatePackages, (packages map (_.value), collectionId))
        )
      else
        PersistenceService.pure(0)

    for {
      collection ← getByIdAux(collectionId)
      existingPackages = collection.packages map Package
      newPackages = packages diff existingPackages
      removedPackages = existingPackages diff packages
      _ ← updatePackagesInfo(newPackages, removedPackages)
    } yield (newPackages, removedPackages)
  }.value

}

object Services {

  implicit val connectionIOMonad: Monad[ConnectionIO] = ScalazInstances[ConnectionIO].monadInstance

  def services(implicit collectionPersistence: Persistence[SharedCollection]) =
    new Services(collectionPersistence)
}

