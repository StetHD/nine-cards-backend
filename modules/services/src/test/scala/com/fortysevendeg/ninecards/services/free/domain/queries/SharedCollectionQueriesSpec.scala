package com.fortysevendeg.ninecards.services.free.domain.queries

import java.sql.Timestamp
import java.time.LocalDateTime

import com.fortysevendeg.ninecards.services.free.domain.SharedCollection.Queries._
import com.fortysevendeg.ninecards.services.persistence.DomainDatabaseContext
import com.fortysevendeg.ninecards.services.persistence.SharedCollectionPersistenceServices.SharedCollectionData
import doobie.contrib.specs2.analysisspec.AnalysisSpec
import org.specs2.mutable.Specification
import shapeless.syntax.std.product._

class SharedCollectionQueriesSpec
  extends Specification
  with AnalysisSpec
  with DomainDatabaseContext {

  val category = "SOCIAL"
  val id = 12345l
  val limit = 25
  val now = Timestamp.valueOf(LocalDateTime.now)
  val offset = 25
  val publicIdentifier = "7a2a4c1c-5260-40a5-ba06-db009a3ef7c4"
  val userId = Option(23456l)

  val data = SharedCollectionData(
    publicIdentifier = publicIdentifier,
    userId           = userId,
    publishedOn      = now,
    description      = Option("Description about the collection"),
    author           = "John Doe",
    name             = "The name of the collection",
    installations    = 1,
    views            = 1,
    category         = category,
    icon             = "path-to-icon",
    community        = true
  )

  val getCollectionByIdQuery = collectionPersistence.generateQuery(
    sql    = getById,
    values = id
  )
  check(getCollectionByIdQuery)

  val getCollectionByPublicIdentifierQuery = collectionPersistence.generateQuery(
    sql    = getByPublicIdentifier,
    values = publicIdentifier
  )
  check(getCollectionByPublicIdentifierQuery)

  val getCollectionsByUserQuery = collectionPersistence.generateQuery(
    sql    = getByUser,
    values = userId
  )
  check(getCollectionsByUserQuery)

  val getLatestCollectionsByCategoryQuery = collectionPersistence.generateQuery(
    sql    = getLatestByCategory,
    values = (category, limit.toString, offset.toString)
  )
  check(getLatestCollectionsByCategoryQuery)

  val getTopCollectionsByCategoryQuery = collectionPersistence.generateQuery(
    sql    = getTopByCategory,
    values = (category, limit.toString, offset.toString)
  )
  check(getTopCollectionsByCategoryQuery)

  val insertCollectionQuery = collectionPersistence.generateUpdateWithGeneratedKeys(
    sql    = insert,
    values = data.toTuple
  )
  check(insertCollectionQuery)

}
