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

package cards.nine.services.free.domain.queries

import java.sql.Timestamp
import java.time.LocalDateTime

import cards.nine.domain.pagination.Page
import cards.nine.services.free.domain.SharedCollection.Queries._
import cards.nine.services.free.domain.SharedCollectionWithAggregatedInfo
import cards.nine.services.free.interpreter.collection.Services.SharedCollectionData
import cards.nine.services.persistence.DomainDatabaseContext
import doobie.contrib.specs2.analysisspec.AnalysisSpec
import doobie.contrib.postgresql.pgtypes._
import org.specs2.mutable.Specification
import shapeless.syntax.std.product._

class SharedCollectionQueriesSpec
    extends Specification
    with AnalysisSpec
    with DomainDatabaseContext {

  val category         = "SOCIAL"
  val id               = 12345l
  val name             = "The best social apps"
  val now              = Timestamp.valueOf(LocalDateTime.now)
  val packages         = List("com.package.one", "com.package.two", "com.package.three")
  val pageParams       = Page(25l, 25l)
  val publicIdentifier = "7a2a4c1c-5260-40a5-ba06-db009a3ef7c4"
  val userId           = Option(23456l)

  val data = SharedCollectionData(
    publicIdentifier = publicIdentifier,
    userId = userId,
    publishedOn = now,
    author = "John Doe",
    name = "The name of the collection",
    views = 1,
    category = category,
    icon = "path-to-icon",
    community = true,
    packages = List("com.package.name")
  )

  val getCollectionByIdQuery = collectionPersistence.generateQuery(
    sql = getById,
    values = id
  )
  check(getCollectionByIdQuery)

  val getCollectionByPublicIdentifierQuery = collectionPersistence.generateQuery(
    sql = getByPublicIdentifier,
    values = publicIdentifier
  )
  check(getCollectionByPublicIdentifierQuery)

  val getCollectionsByUserQuery =
    collectionPersistence.generateQueryFor[SharedCollectionWithAggregatedInfo](
      sql = getByUser,
      values = userId
    )
  check(getCollectionsByUserQuery)

  val getLatestCollectionsByCategoryQuery = collectionPersistence.generateQuery(
    sql = getLatestByCategory,
    values = (category, pageParams.pageSize, pageParams.pageNumber)
  )
  check(getLatestCollectionsByCategoryQuery)

  val getTopCollectionsByCategoryQuery = collectionPersistence.generateQuery(
    sql = getTopByCategory,
    values = (category, pageParams.pageSize, pageParams.pageNumber)
  )
  check(getTopCollectionsByCategoryQuery)

  val insertCollectionQuery = collectionPersistence.generateUpdateWithGeneratedKeys(
    sql = insert,
    values = data.toTuple
  )
  check(insertCollectionQuery)

  val increaseViewsByOneQuery = collectionPersistence.generateUpdateWithGeneratedKeys(
    sql = increaseViewsByOne,
    values = id
  )
  check(increaseViewsByOneQuery)

  val updateCollectionQuery = collectionPersistence.generateUpdateWithGeneratedKeys(
    sql = update,
    values = (name, id)
  )
  check(updateCollectionQuery)

  val updatePackagesQuery = collectionPersistence.generateUpdateWithGeneratedKeys(
    sql = updatePackages,
    values = (packages, id)
  )
  check(updatePackagesQuery)
}
