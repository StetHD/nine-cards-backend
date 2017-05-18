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

package cards.nine.processes.collections

import cards.nine.commons.NineCardsService
import cards.nine.commons.NineCardsService.NineCardsService
import cards.nine.domain.application.{BasicCard, CardList, Package}
import cards.nine.domain.market.MarketCredentials
import cards.nine.domain.pagination.Page
import cards.nine.processes.collections.messages._
import cards.nine.services.free.algebra
import cards.nine.services.free.algebra.{Firebase, GooglePlay}
import cards.nine.services.free.domain.Firebase._
import cards.nine.services.free.domain.{BaseSharedCollection, SharedCollectionSubscription}

class SharedCollectionProcesses[F[_]](
    implicit collectionServices: algebra.SharedCollection.Services[F],
    notificationsServices: Firebase.Services[F],
    googlePlayServices: GooglePlay.Services[F],
    subscriptionServices: algebra.Subscription.Services[F],
    userServices: algebra.User.Services[F]
) {

  import Converters._

  def createCollection(
      request: CreateCollectionRequest): NineCardsService[F, CreateOrUpdateCollectionResponse] =
    collectionServices.add(toSharedCollectionDataServices(request.collection)) map { collection ⇒
      CreateOrUpdateCollectionResponse(
        publicIdentifier = collection.publicIdentifier,
        packagesStats = PackagesStats(added = collection.packages.size)
      )
    }

  def getCollectionByPublicIdentifier(
      userId: Long,
      publicIdentifier: String,
      marketAuth: MarketCredentials
  ): NineCardsService[F, GetCollectionByPublicIdentifierResponse] =
    for {
      sharedCollection ← collectionServices.getByPublicId(publicIdentifier)
      collection = toSharedCollection(sharedCollection, userId)
      appsInfo ← googlePlayServices.resolveManyDetailed(collection.packages, marketAuth)
    } yield
      GetCollectionByPublicIdentifierResponse(
        toSharedCollectionWithAppsInfo(collection, appsInfo.cards)
      )

  def getLatestCollectionsByCategory(
      userId: Long,
      category: String,
      marketAuth: MarketCredentials,
      pageParams: Page
  ): NineCardsService[F, GetCollectionsResponse] =
    getCollections(
      collectionServices.getLatestByCategory(category, pageParams),
      userId,
      marketAuth)

  def getPublishedCollections(
      userId: Long,
      marketAuth: MarketCredentials
  ): NineCardsService[F, GetCollectionsResponse] =
    getCollections(collectionServices.getByUser(userId), userId, marketAuth)

  def getTopCollectionsByCategory(
      userId: Long,
      category: String,
      marketAuth: MarketCredentials,
      pageParams: Page
  ): NineCardsService[F, GetCollectionsResponse] =
    getCollections(collectionServices.getTopByCategory(category, pageParams), userId, marketAuth)

  def getSubscriptionsByUser(user: Long): NineCardsService[F, GetSubscriptionsByUserResponse] =
    subscriptionServices.getByUser(user) map toGetSubscriptionsByUserResponse

  def subscribe(publicIdentifier: String, user: Long): NineCardsService[F, SubscribeResponse] = {

    def addSubscription(subscription: Option[SharedCollectionSubscription], collectionId: Long) = {
      val subscriptionCount = 1

      subscription
        .fold(subscriptionServices.add(collectionId, user, publicIdentifier))(_ ⇒
          NineCardsService.right(subscriptionCount))
    }

    for {
      collection   ← collectionServices.getByPublicId(publicIdentifier)
      subscription ← subscriptionServices.getByCollectionAndUser(collection.id, user)
      _            ← addSubscription(subscription, collection.id)
    } yield SubscribeResponse()
  }

  def unsubscribe(
      publicIdentifier: String,
      userId: Long): NineCardsService[F, UnsubscribeResponse] =
    for {
      collection ← collectionServices.getByPublicId(publicIdentifier)
      _          ← subscriptionServices.removeByCollectionAndUser(collection.id, userId)
    } yield UnsubscribeResponse()

  def sendNotifications(
      publicIdentifier: String,
      packagesName: List[Package]
  ): NineCardsService[F, SendNotificationResponse] =
    if (packagesName.isEmpty)
      NineCardsService.right[F, SendNotificationResponse](SendNotificationResponse.emptyResponse)
    else {
      for {
        subscribers ← userServices.getSubscribedInstallationByCollection(publicIdentifier)
        response ← notificationsServices.sendUpdatedCollectionNotification(
          UpdatedCollectionNotificationInfo(
            deviceTokens = subscribers flatMap (_.deviceToken),
            publicIdentifier = publicIdentifier,
            packagesName = packagesName
          )
        )
      } yield response
    }

  def increaseViewsCountByOne(
      publicIdentifier: String
  ): NineCardsService[F, IncreaseViewsCountByOneResponse] =
    for {
      collection ← collectionServices.getByPublicId(publicIdentifier)
      _          ← collectionServices.increaseViewsByOne(collection.id)
    } yield IncreaseViewsCountByOneResponse(collection.publicIdentifier)

  def updateCollection(
      publicIdentifier: String,
      collectionInfo: Option[SharedCollectionUpdateInfo],
      packages: Option[List[Package]]
  ): NineCardsService[F, CreateOrUpdateCollectionResponse] = {

    def updateCollectionInfo(collectionId: Long, info: Option[SharedCollectionUpdateInfo]) =
      info
        .fold(NineCardsService.right[F, Int](0))(
          updateInfo ⇒ collectionServices.update(collectionId, updateInfo.title)
        )

    def updatePackages(collectionId: Long, packagesName: Option[List[Package]]) =
      packagesName
        .fold(NineCardsService.right[F, (List[Package], List[Package])]((Nil, Nil)))(
          packages ⇒ collectionServices.updatePackages(collectionId, packages)
        )

    for {
      collection    ← collectionServices.getByPublicId(publicIdentifier)
      _             ← updateCollectionInfo(collection.id, collectionInfo)
      packagesStats ← updatePackages(collection.id, packages)
      (addedPackages, removedPackages) = packagesStats
      _ ← sendNotifications(publicIdentifier, addedPackages)
    } yield
      CreateOrUpdateCollectionResponse(
        publicIdentifier,
        packagesStats = PackagesStats(addedPackages.size, Option(removedPackages.size))
      )
  }

  private def getCollections[T <: BaseSharedCollection](
      sharedCollections: NineCardsService[F, List[T]],
      userId: Long,
      marketAuth: MarketCredentials
  ) = {

    def fillGooglePlayInfoForPackages(appsInfo: CardList[BasicCard])(
        collection: SharedCollection) = {
      val foundAppInfo = appsInfo.cards.filter(a ⇒ collection.packages.contains(a.packageName))
      toSharedCollectionWithAppsInfo(collection, foundAppInfo)
    }

    for {
      collections ← sharedCollections map toSharedCollectionList(userId)
      packages = collections.flatMap(_.packages).distinct
      appsInfo ← googlePlayServices.resolveManyBasic(packages, marketAuth)
    } yield
      GetCollectionsResponse(
        collections map fillGooglePlayInfoForPackages(appsInfo)
      )
  }
}

object SharedCollectionProcesses {

  implicit def processes[F[_]](
      implicit collectionServices: algebra.SharedCollection.Services[F],
      notificationsServices: Firebase.Services[F],
      googlePlayServices: GooglePlay.Services[F],
      subscriptionServices: algebra.Subscription.Services[F],
      userServices: algebra.User.Services[F]
  ) = new SharedCollectionProcesses

}
