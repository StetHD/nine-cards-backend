package cards.nine.services.free.interpreter.subscription

import cards.nine.services.free.algebra.Subscription
import cards.nine.services.free.domain.SharedCollectionSubscription
import cards.nine.services.free.domain.SharedCollectionSubscription.Queries
import cards.nine.services.persistence.Persistence
import doobie.imports._

class Services(persistence: Persistence[SharedCollectionSubscription]) extends Subscription.Services.Interpreter[ConnectionIO] {

  def add(collectionId: Long, userId: Long, collectionPublicId: String): ConnectionIO[Int] =
    persistence.update(
      sql    = Queries.insert,
      values = (collectionId, userId, collectionPublicId)
    )

  def getByCollection(collectionId: Long): ConnectionIO[List[SharedCollectionSubscription]] =
    persistence.fetchList(
      sql    = Queries.getByCollection,
      values = collectionId
    )

  def getByCollectionAndUser(collectionId: Long, userId: Long): ConnectionIO[Option[SharedCollectionSubscription]] =
    persistence.fetchOption(
      sql    = Queries.getByCollectionAndUser,
      values = (collectionId, userId)
    )

  def getByUser(userId: Long): ConnectionIO[List[SharedCollectionSubscription]] =
    persistence.fetchList(
      sql    = Queries.getByUser,
      values = userId
    )

  def removeByCollectionAndUser(collectionId: Long, userId: Long): ConnectionIO[Int] =
    persistence.update(
      sql    = Queries.deleteByCollectionAndUser,
      values = (collectionId, userId)
    )

  def addImpl(collection: Long, user: Long, collectionPublicId: String): ConnectionIO[Int] = add(collection, user, collectionPublicId)
  def getByCollectionAndUserImpl(collection: Long, user: Long): ConnectionIO[Option[cards.nine.services.free.domain.SharedCollectionSubscription]] = getByCollectionAndUser(collection, user)
  def getByCollectionImpl(collection: Long): ConnectionIO[List[cards.nine.services.free.domain.SharedCollectionSubscription]] = getByCollection(collection)
  def getByUserImpl(user: Long): ConnectionIO[List[cards.nine.services.free.domain.SharedCollectionSubscription]] = getByUser(user)
  def removeByCollectionAndUserImpl(collection: Long, user: Long): ConnectionIO[Int] = removeByCollectionAndUser(collection, user)
}

object Services {

  def services(implicit persistence: Persistence[SharedCollectionSubscription]) =
    new Services(persistence)
}
