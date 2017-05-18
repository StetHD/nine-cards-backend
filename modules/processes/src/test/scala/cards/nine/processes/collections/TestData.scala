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

import java.sql.Timestamp
import java.time.Instant

import cards.nine.commons.NineCardsErrors.SharedCollectionNotFound
import cards.nine.domain.account.{AndroidId, DeviceToken}
import cards.nine.domain.application.{CardList, FullCard, Package}
import cards.nine.domain.market.{Localization, MarketCredentials, MarketToken}
import cards.nine.domain.pagination.Page
import cards.nine.processes.collections.messages._
import cards.nine.services.free.domain.{SharedCollection ⇒ SharedCollectionServices, _}
import cards.nine.services.free.domain.Firebase.{
  NotificationIndividualResult,
  SendNotificationResponse
}
import cards.nine.services.free.interpreter.collection.Services.{
  SharedCollectionData ⇒ SharedCollectionDataServices
}
import org.joda.time.DateTime

private[collections] object TestData {

  val addedPackagesCount = 2

  val androidId = AndroidId("aaaaaaaaaaaaaaaa")

  val appCategory = "COUNTRY"

  val author = "John Doe"

  val canonicalId = 0

  val category = "SOCIAL"

  val collectionId = 1l

  val community = true

  val countryContinent = "Americas"

  val countryIsoCode2 = "US"

  val countryIsoCode3 = "USA"

  val countryName = "United States"

  val deviceToken = DeviceToken("77777777-7777-7777-7777-777777777777")

  val failure = 0

  val icon = "path-to-icon"

  val installationId = 10l

  val localization = Localization("en-EN")

  val messageId = "55555555-5555"

  val millis = 1453226400000l

  val multicastId = 15L

  val name = "The best social media apps"

  val pageNumber = 0

  val pageSize = 25

  val pageParams = Page(pageNumber, pageSize)

  val publicIdentifier = "77777777-7777"

  val publishedOnDatetime = new DateTime(millis)

  val publishedOnTimestamp = Timestamp.from(Instant.ofEpochMilli(millis))

  val publisherId = 27L

  val removedPackagesCount = 1

  val stars = 5.0d

  val subscriberId = 42L

  val subscriptionsCount = 5L

  val success = 1

  val token = "99999999-9999"

  val updatedCollectionsCount = 1

  val userId = Option(publisherId)

  val views = 1

  val packagesName = List(
    "earth.europe.italy",
    "earth.europe.unitedKingdom",
    "earth.europe.germany",
    "earth.europe.france",
    "earth.europe.portugal",
    "earth.europe.spain"
  ) map Package

  val missing = List(
    "earth.europe.italy",
    "earth.europe.unitedKingdom"
  ) map Package

  val updatePackagesName = List(
    "earth.europe.italy",
    "earth.europe.unitedKingdom",
    "earth.europe.germany"
  ) map Package

  val addedPackages = List(
    "earth.europe.italy",
    "earth.europe.unitedKingdom"
  ) map Package

  val removedPackages = List(
    "earth.europe.germany"
  ) map Package

  val updatedPackages = (addedPackages, removedPackages)

  object Values {

    val apps = packagesName map { packageName ⇒
      FullCard(
        packageName = packageName,
        title = "Germany",
        free = true,
        icon = icon,
        stars = stars,
        downloads = "100.000+",
        categories = List(appCategory),
        screenshots = Nil
      )
    }

    val appsInfo      = CardList(missing, Nil, apps)
    val appsInfoBasic = CardList(missing, Nil, apps.map(_.toBasic))

    val marketAuth = MarketCredentials(
      androidId = androidId,
      localization = Some(localization),
      token = MarketToken(token)
    )

    val collection = SharedCollectionServices(
      id = collectionId,
      publicIdentifier = publicIdentifier,
      userId = userId,
      publishedOn = publishedOnTimestamp,
      author = author,
      name = name,
      views = views,
      category = category,
      icon = icon,
      community = community,
      packages = packagesName map (_.value)
    )

    val collectionWithSubscriptions = SharedCollectionWithAggregatedInfo(
      sharedCollectionData = collection,
      subscriptionsCount = subscriptionsCount
    )

    val sharedCollectionNotFoundError = SharedCollectionNotFound("Shared collection not found")

    val createPackagesStats = PackagesStats(packagesName.size, None)

    val updatePackagesStats = PackagesStats(addedPackagesCount, Option(removedPackagesCount))

    val appInfoList = packagesName map { packageName ⇒
      FullCard(packageName, "Germany", List(appCategory), "100.000+", true, icon, Nil, stars)
    }

    val installation = Installation(
      id = installationId,
      userId = subscriberId,
      deviceToken = Option(deviceToken),
      androidId = androidId
    )

    val sendNotificationResponse = SendNotificationResponse(
      multicastIds = List(multicastId),
      success = success,
      failure = failure,
      canonicalIds = canonicalId,
      results = List(
        NotificationIndividualResult(
          message_id = Option(messageId),
          registration_id = None,
          error = None
        ))
    )

    val sharedCollectionDataServices = SharedCollectionDataServices(
      publicIdentifier = publicIdentifier,
      userId = userId,
      publishedOn = publishedOnTimestamp,
      author = author,
      name = name,
      views = views,
      category = category,
      icon = icon,
      community = community,
      packages = packagesName map (_.value)
    )

    val sharedCollectionData = SharedCollectionData(
      publicIdentifier = publicIdentifier,
      userId = userId,
      publishedOn = publishedOnDatetime,
      author = author,
      name = name,
      views = Option(views),
      category = category,
      icon = icon,
      community = community,
      packages = packagesName
    )

    val sharedCollection = SharedCollection(
      publicIdentifier = publicIdentifier,
      publishedOn = new DateTime(publishedOnTimestamp.getTime),
      author = author,
      name = name,
      views = views,
      category = category,
      icon = icon,
      community = community,
      owned = true,
      packages = packagesName
    )

    val sharedCollectionWithSubscriptions = SharedCollection(
      publicIdentifier = publicIdentifier,
      publishedOn = new DateTime(publishedOnTimestamp.getTime),
      author = author,
      name = name,
      views = views,
      category = category,
      icon = icon,
      community = community,
      owned = true,
      packages = packagesName,
      subscriptionsCount = Option(subscriptionsCount)
    )

    val sharedCollectionUpdateInfo = SharedCollectionUpdateInfo(
      title = name
    )

    val sharedCollectionWithAppsInfo = SharedCollectionWithAppsInfo(
      collection = sharedCollection,
      appsInfo = appInfoList
    )

    val sharedCollectionWithAppsInfoBasic = SharedCollectionWithAppsInfo(
      collection = sharedCollection,
      appsInfo = appInfoList map (_.toBasic)
    )

    val sharedCollectionWithAppsInfoAndSubscriptions = SharedCollectionWithAppsInfo(
      collection = sharedCollectionWithSubscriptions,
      appsInfo = appInfoList map (_.toBasic)
    )

    val subscription = SharedCollectionSubscription(
      sharedCollectionId = collectionId,
      userId = subscriberId,
      sharedCollectionPublicId = publicIdentifier
    )

    val updatedSubscriptionsCount = 1
  }

  object Messages {

    import Values._

    val createCollectionRequest: CreateCollectionRequest = CreateCollectionRequest(
      collection = sharedCollectionData
    )

    val createCollectionResponse = CreateOrUpdateCollectionResponse(
      publicIdentifier = publicIdentifier,
      packagesStats = createPackagesStats
    )

    val getCollectionByPublicIdentifierResponse = GetCollectionByPublicIdentifierResponse(
      data = sharedCollectionWithAppsInfo
    )

  }

}
