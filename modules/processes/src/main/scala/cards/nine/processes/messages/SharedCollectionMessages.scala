package cards.nine.processes.messages

import cards.nine.domain.application.{ BasicCard, FullCard, Package }
import cats.data.Xor
import org.joda.time.DateTime

object SharedCollectionMessages {

  case class SharedCollectionData(
    publicIdentifier: String,
    userId: Option[Long],
    publishedOn: DateTime,
    author: String,
    name: String,
    installations: Option[Int] = None,
    views: Option[Int] = None,
    category: String,
    icon: String,
    community: Boolean
  )

  case class SharedCollectionWithAppsInfo[A](
    collection: SharedCollection,
    appsInfo: List[A]
  )

  case class SharedCollection(
    publicIdentifier: String,
    publishedOn: DateTime,
    author: String,
    name: String,
    installations: Int,
    views: Int,
    category: String,
    icon: String,
    community: Boolean,
    owned: Boolean,
    packages: List[Package],
    subscriptionsCount: Option[Long] = None
  )

  case class SharedCollectionUpdateInfo(
    title: String
  )

  case class CreateCollectionRequest(
    collection: SharedCollectionData,
    packages: List[Package]
  )

  case class PackagesStats(added: Int, removed: Option[Int] = None)

  case class CreateOrUpdateCollectionResponse(
    publicIdentifier: String,
    packagesStats: PackagesStats
  )

  case class GetCollectionByPublicIdentifierResponse(data: SharedCollectionWithAppsInfo[FullCard])

  case class GetSubscriptionsByUserResponse(subscriptions: List[String])

  case class SubscribeResponse()

  case class UnsubscribeResponse()

  case class GetCollectionsResponse(collections: List[SharedCollectionWithAppsInfo[BasicCard]])

  type XorGetCollectionByPublicId = Xor[Throwable, GetCollectionByPublicIdentifierResponse]

}
