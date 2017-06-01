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
package cards.nine.processes.rankings

import cards.nine.commons.NineCardsErrors.CountryNotFound
import cards.nine.commons.NineCardsService
import cards.nine.commons.NineCardsService._
import cards.nine.domain.analytics._
import cards.nine.domain.application.{ Category, Moment, Package }
import cards.nine.services.free.algebra._
import cards.nine.services.free.domain.Ranking.GoogleAnalyticsRanking
import cats.instances.list._
import cats.instances.map._
import cats.syntax.semigroup._
import cats.syntax.traverse._
import freestyle.FreeS

class RankingProcesses[F[_]](
  implicit
  analytics: GoogleAnalytics[F],
  countryR: CountryR[F],
  oauthServices: GoogleOAuth[F],
  rankingS: RankingS[F]
) {
  private[this] val allCategories = Category.valuesName ++ Moment.valuesName ++ Moment.widgetValuesName

  implicit val v = NineCardsService.monadError[F]

  import messages._
  import Converters._

  def toNCS[A](fs: FreeS.Par[F, Result[A]]): NineCardsService[F, A] = NineCardsService[F, A](fs.monad)

  def getRanking(scope: GeoScope): NineCardsService[F, Get.Response] =
    (toNCS(rankingS.getRanking(scope)) map Get.Response)

  def reloadRankingForCountries(request: Reload.Request): NineCardsService[F, Reload.SummaryResponse] = {
    import request._

    def generateRankings(
      countries: List[CountryIsoCode], params: RankingParams
    ): NineCardsService[F, List[UpdateRankingSummary]] = {

      def generateRanking(countryCode: CountryIsoCode): NineCardsService[F, UpdateRankingSummary] = {
        for {
          ranking ← toNCS(analytics.getRanking(Option(countryCode), allCategories, params))
          summary ← toNCS(rankingS.updateRanking(CountryScope(countryCode), ranking))
        } yield summary
      }
      countries.traverse[NineCardsService[F, ?], UpdateRankingSummary](generateRanking)
    }

    for {
      accessToken ← toNCS(oauthServices.fetchAcessToken(serviceAccount))
      params = RankingParams(dateRange, rankingLength, AnalyticsToken(accessToken.value))
      countries ← toNCS(countryR.getCountries(pageParams))
      countriesWithRanking ← toNCS(analytics.getCountriesWithRanking(params))
      countriesCode = countries.map(c ⇒ CountryIsoCode(c.isoCode2))
      selectedCountries = countriesCode.intersect(countriesWithRanking.countries)
      updateRankingsSummary ← generateRankings(selectedCountries, params)
    } yield Reload.SummaryResponse(
      countriesWithoutRanking = countriesCode diff selectedCountries,
      countriesWithRanking    = updateRankingsSummary
    )
  }

  def reloadRankingByScope(scope: GeoScope, params: RankingParams): NineCardsService[F, Reload.Response] = {

    def hasRankingInfo(code: CountryIsoCode, countries: List[CountryIsoCode]) =
      countries.exists(_.value.equalsIgnoreCase(code.value))

    def generateRanking(scope: GeoScope, countries: List[CountryIsoCode]): NineCardsService[F, GoogleAnalyticsRanking] =
      scope match {
        case WorldScope ⇒ toNCS(analytics.getRanking(None, allCategories, params))
        case CountryScope(code) if hasRankingInfo(code, countries) ⇒
          toNCS(analytics.getRanking(Option(code), allCategories, params))
        case _ ⇒
          NineCardsService.left[F, GoogleAnalyticsRanking](CountryNotFound("The country doesn't have ranking info"))
      }

    for {
      countriesWithRanking ← toNCS(analytics.getCountriesWithRanking(params))
      ranking ← generateRanking(scope, countriesWithRanking.countries)
      _ ← toNCS(rankingS.updateRanking(scope, ranking))
    } yield Reload.Response()
  }

  def getRankedDeviceApps(
    location: Option[String],
    deviceApps: Map[String, List[Package]]
  ): NineCardsService[F, List[RankedAppsByCategory]] = {

    def unifyDeviceApps(deviceApps: Map[String, List[Package]]) = {
      val (games, otherApps) = deviceApps.partition { case (cat, _) ⇒ cat.matches("GAME\\_.*") }

      if (games.isEmpty)
        otherApps
      else
        otherApps.combine(Map("GAME" → games.flatMap { case (cat, apps) ⇒ apps }.toList))
    }

    if (deviceApps.isEmpty)
      NineCardsService.pure(List.empty[RankedAppsByCategory])
    else {
      val unifiedDeviceApps = unifyDeviceApps(deviceApps)
      val unrankedApps = unifiedDeviceApps.flatMap {
        case (cat, apps) ⇒ apps map toUnrankedApp(cat)
      }.toSet

      for {
        geoScope ← location.fold(NineCardsService.pure[F, GeoScope](WorldScope))(geoScopeFromLocation)
        rankedApps ← toNCS(rankingS.rankApps(geoScope, unrankedApps))
        rankedAppsByCategory = rankedApps.groupBy(_.category)
        unrankedDeviceApps = unifiedDeviceApps map {
          case (category, apps) ⇒
            val appWithoutRanking = apps
              .diff(rankedAppsByCategory.getOrElse(category, Nil).map(_.packageName))
              .map(RankedApp(_, category, None))
            (category, appWithoutRanking)
        }
      } yield (rankedAppsByCategory combine unrankedDeviceApps)
        .map(toRankedAppsByCategory(limit = None))
        .toList
        .sortBy(r ⇒ Category.sortedValues.indexOf(r.category))
    }
  }

  def getRankedAppsByMoment(
    location: Option[String],
    deviceApps: List[Package],
    moments: List[String],
    limit: Int
  ): NineCardsService[F, List[RankedAppsByCategory]] =
    if (deviceApps.isEmpty)
      NineCardsService.pure(List.empty[RankedAppsByCategory])
    else
      for {
        geoScope ← location.fold(NineCardsService.pure[F, GeoScope](WorldScope))(geoScopeFromLocation)
        rankedApps ← toNCS(rankingS.rankAppsWithinMoments(geoScope, deviceApps, moments))
      } yield rankedApps
        .groupBy(_.category)
        .map(toRankedAppsByCategory(limit = Option(limit)))
        .toList

  def getRankedWidgets(
    location: Option[String],
    apps: List[Package],
    moments: List[String],
    limit: Int
  ): NineCardsService[F, List[RankedWidgetsByMoment]] =
    if (apps.isEmpty)
      NineCardsService.pure(List.empty[RankedWidgetsByMoment])
    else {
      for {
        geoScope ← location match {
          case None ⇒ NineCardsService.pure[F, GeoScope](WorldScope)
          case Some(loc) ⇒ geoScopeFromLocation(loc)
        }
        rankedWidgets ← toNCS(rankingS.rankWidgets(geoScope, apps, moments map toWidgetMoment))
      } yield rankedWidgets
        .groupBy(_.moment)
        .map(toRankedWidgetsByMoment(limit))
        .toList
    }

  private[this] def geoScopeFromLocation(isoCode: String): NineCardsService[F, GeoScope] =
    toNCS(countryR.getCountryByIsoCode2(isoCode.toUpperCase))
      .map { country ⇒
        val scope: GeoScope = CountryScope(CountryIsoCode(country.isoCode2))
        scope
      }
      .recover { case _: CountryNotFound ⇒ WorldScope }

}

object RankingProcesses {

  implicit def processes[F[_]](
    implicit
    analytics: GoogleAnalytics[F],
    countryR: CountryR[F],
    oauthServices: GoogleOAuth[F],
    rankingS: RankingS[F]
  ) = new RankingProcesses

}
