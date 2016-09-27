package cards.nine.processes

import cards.nine.processes.ProcessesExceptions.SharedCollectionNotFoundException
import cards.nine.processes.converters.Converters._
import cards.nine.processes.messages.GooglePlayAuthMessages.AuthParams
import cards.nine.processes.messages.SharedCollectionMessages._
import cards.nine.processes.utils.XorTSyntax._
import cards.nine.services.common.FreeUtils._
import cards.nine.services.free.algebra
import cards.nine.services.free.algebra.{ Firebase, GooglePlay }
import cards.nine.services.free.domain.Firebase._
import cards.nine.services.free.domain.GooglePlay.AppsInfo
import cards.nine.services.free.domain.{ BaseSharedCollection, Installation, SharedCollectionSubscription }
import cats.data.{ Xor, XorT }
import cats.free.Free
import cats.instances.list._
import cats.syntax.traverse._

class SharedCollectionProcesses[F[_]](
  implicit
  collectionServices: algebra.SharedCollection.Services[F],
  firebaseNotificationsServices: Firebase.Services[F],
  googlePlayServices: GooglePlay.Services[F],
  subscriptionServices: algebra.Subscription.Services[F],
  userServices: algebra.User.Services[F]
) {

  val sharedCollectionNotFoundException = SharedCollectionNotFoundException(
    message = "The required shared collection doesn't exist"
  )

  def createCollection(request: CreateCollectionRequest): Free[F, CreateOrUpdateCollectionResponse] = {
    for {
      collection ← collectionServices.add(request.collection)
      addedPackages ← collectionServices.addPackages(collection.id, request.packages)
    } yield CreateOrUpdateCollectionResponse(
      publicIdentifier = collection.publicIdentifier,
      packagesStats    = PackagesStats(added = addedPackages)
    )
  }

  def getCollectionByPublicIdentifier(
    publicIdentifier: String,
    authParams: AuthParams
  ): Free[F, XorGetCollectionByPublicId] = {
    for {
      sharedCollection ← findCollection(publicIdentifier)
      sharedCollectionInfo ← getCollectionPackages(sharedCollection).rightXorT[Throwable]
      resolvedSharedCollection ← getAppsInfoForCollection(sharedCollectionInfo, authParams)
    } yield resolvedSharedCollection
  }.value

  def getLatestCollectionsByCategory(
    category: String,
    authParams: AuthParams,
    pageNumber: Int,
    pageSize: Int
  ): Free[F, GetCollectionsResponse] =
    getCollections(collectionServices.getLatestByCategory(category, pageNumber, pageSize), authParams)

  def getPublishedCollections(
    userId: Long,
    authParams: AuthParams
  ): Free[F, GetCollectionsResponse] =
    getCollections(collectionServices.getByUser(userId), authParams)

  def getTopCollectionsByCategory(
    category: String,
    authParams: AuthParams,
    pageNumber: Int,
    pageSize: Int
  ): Free[F, GetCollectionsResponse] =
    getCollections(collectionServices.getTopByCategory(category, pageNumber, pageSize), authParams)

  /**
    * This process changes the application state to one where the user is subscribed to the collection.
    */

  def getSubscriptionsByUser(user: Long): Free[F, GetSubscriptionsByUserResponse] =
    subscriptionServices.getByUser(user) map toGetSubscriptionsByUserResponse

  def subscribe(publicIdentifier: String, user: Long): Free[F, Xor[Throwable, SubscribeResponse]] = {

    def addSubscription(
      subscription: Option[SharedCollectionSubscription],
      collectionId: Long,
      collectionPublicId: String
    ) = {
      val subscriptionCount = 1

      subscription
        .fold(subscriptionServices.add(collectionId, user, collectionPublicId))(_ ⇒ subscriptionCount.toFree)
        .map(_ ⇒ SubscribeResponse())
    }

    for {
      collection ← findCollection(publicIdentifier)
      subscription ← subscriptionServices.getByCollectionAndUser(collection.id, user).rightXorT[Throwable]
      subscriptionInfo ← addSubscription(subscription, collection.id, collection.publicIdentifier).rightXorT[Throwable]
    } yield subscriptionInfo
  }.value

  def unsubscribe(publicIdentifier: String, userId: Long): Free[F, Xor[Throwable, UnsubscribeResponse]] = {
    for {
      collection ← findCollection(publicIdentifier)
      _ ← subscriptionServices.removeByCollectionAndUser(collection.id, userId).rightXorT[Throwable]
    } yield UnsubscribeResponse()
  }.value

  def sendNotifications(
    publicIdentifier: String,
    packagesName: List[String]
  ): Free[F, List[FirebaseError Xor NotificationResponse]] = {

    def toUpdateCollectionNotificationInfoList(installations: List[Installation]) =
      installations.flatMap(_.deviceToken).grouped(1000).toList

    def sendNotificationsByDeviceTokenGroup(
      publicIdentifier: String,
      packagesName: List[String]
    )(
      deviceTokens: List[String]
    ) =
      firebaseNotificationsServices.sendUpdatedCollectionNotification(
        UpdatedCollectionNotificationInfo(deviceTokens, publicIdentifier, packagesName)
      )

    if (packagesName.isEmpty)
      List.empty[FirebaseError Xor NotificationResponse].toFree
    else
      userServices.getSubscribedInstallationByCollection(publicIdentifier) flatMap {
        installations ⇒
          toUpdateCollectionNotificationInfoList(installations)
            .traverse[Free[F, ?], FirebaseError Xor NotificationResponse] {
              sendNotificationsByDeviceTokenGroup(publicIdentifier, packagesName)
            }
      }
  }

  def updateCollection(
    publicIdentifier: String,
    collectionInfo: Option[SharedCollectionUpdateInfo],
    packages: Option[List[String]]
  ): Free[F, Xor[Throwable, CreateOrUpdateCollectionResponse]] = {

    def updateCollectionInfo(collectionId: Long, info: Option[SharedCollectionUpdateInfo]) =
      info
        .map(c ⇒ collectionServices.update(collectionId, c.title))
        .getOrElse(0.toFree)

    def updatePackages(collectionId: Long, packagesName: Option[List[String]]) =
      packagesName
        .map(p ⇒ collectionServices.updatePackages(collectionId, p))
        .getOrElse((List.empty[String], List.empty[String]).toFree)

    def updateCollectionAndPackages(
      publicIdentifier: String,
      collectionInfo: Option[SharedCollectionUpdateInfo],
      packages: Option[List[String]]
    ): Free[F, Throwable Xor (List[String], List[String])] = {
      for {
        collection ← findCollection(publicIdentifier)
        _ ← updateCollectionInfo(collection.id, collectionInfo).rightXorT[Throwable]
        info ← updatePackages(collection.id, packages).rightXorT[Throwable]
      } yield info
    }.value

    {
      for {
        updateInfo ← updateCollectionAndPackages(publicIdentifier, collectionInfo, packages).toXorT
        (addedPackages, removedPackages) = updateInfo
        _ ← sendNotifications(publicIdentifier, addedPackages).rightXorT[Throwable]
      } yield CreateOrUpdateCollectionResponse(
        publicIdentifier,
        packagesStats = (PackagesStats.apply _).tupled((addedPackages.size, Option(removedPackages.size)))
      )
    }.value
  }

  private[this] def findCollection(publicId: String) =
    collectionServices
      .getByPublicId(publicId)
      .map(Xor.fromOption(_, sharedCollectionNotFoundException))
      .toXorT

  private def getAppsInfoForCollection(
    collection: SharedCollection,
    authParams: AuthParams
  ): XorT[Free[F, ?], Throwable, GetCollectionByPublicIdentifierResponse] = {
    googlePlayServices.resolveMany(collection.packages, toAuthParamsServices(authParams)) map { appsInfo ⇒
      GetCollectionByPublicIdentifierResponse(
        toSharedCollectionWithAppsInfo(collection, appsInfo.apps)
      )
    }
  }.rightXorT[Throwable]

  private def getCollections[T <: BaseSharedCollection](
    sharedCollections: Free[F, List[T]],
    authParams: AuthParams
  ) = {

    def getGooglePlayInfoForPackages(
      collections: List[SharedCollection],
      authParams: AuthParams
    ): Free[F, AppsInfo] = {
      val packages = collections.flatMap(_.packages).toSet.toList
      googlePlayServices.resolveMany(packages, toAuthParamsServices(authParams))
    }

    def fillGooglePlayInfoForPackages(
      collections: List[SharedCollection],
      appsInfo: AppsInfo
    ) = GetCollectionsResponse {
      collections map { collection ⇒
        val foundAppInfo = appsInfo.apps.filter(a ⇒ collection.packages.contains(a.packageName))

        toSharedCollectionWithAppsInfo(collection, foundAppInfo)
      }
    }

    val collectionsWithPackages = sharedCollections flatMap { collections ⇒
      collections.traverse[Free[F, ?], SharedCollection](getCollectionPackages)
    }

    for {
      collections ← collectionsWithPackages
      appsInfo ← getGooglePlayInfoForPackages(collections, authParams)
    } yield fillGooglePlayInfoForPackages(collections, appsInfo)
  }

  private[this] def getCollectionPackages(collection: BaseSharedCollection) =
    collectionServices.getPackagesByCollection(collection.sharedCollectionId) map { packages ⇒
      toSharedCollection(collection, packages map (_.packageName))
    }
}

object SharedCollectionProcesses {

  implicit def processes[F[_]](
    implicit
    collectionServices: algebra.SharedCollection.Services[F],
    firebaseNotificationsServices: Firebase.Services[F],
    googlePlayServices: GooglePlay.Services[F],
    subscriptionServices: algebra.Subscription.Services[F],
    userServices: algebra.User.Services[F]
  ) = new SharedCollectionProcesses

}