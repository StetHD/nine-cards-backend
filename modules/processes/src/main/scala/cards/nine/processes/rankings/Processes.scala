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
import cards.nine.domain.application.{Category, Moment, Package}
import cards.nine.services.free.algebra
import cards.nine.services.free.algebra.GoogleAnalytics
import cards.nine.services.free.domain.Ranking.GoogleAnalyticsRanking
import cats.free.Free
import cats.instances.all._
import cats.syntax.semigroup._
import cats.syntax.traverse._

class RankingProcesses[F[_]](
    implicit analytics: GoogleAnalytics.Services[F],
    countryPersistence: algebra.Country.Services[F],
    oauthServices: algebra.GoogleOAuth.Services[F],
    rankingServices: algebra.Ranking.Services[F]
) {
  private[this] val allCategories = Category.valuesName ++ Moment.valuesName ++ Moment.widgetValuesName

  import messages._
  import Converters._

  def getRanking(scope: GeoScope): Free[F, Result[Get.Response]] =
    (rankingServices.getRanking(scope) map Get.Response).value

  def reloadRankingForCountries(request: Reload.Request): Free[F, Result[Reload.SummaryResponse]] = {
    import request._

    def generateRankings(
        countries: List[CountryIsoCode],
        params: RankingParams
    ): NineCardsService[F, List[UpdateRankingSummary]] = {

      def generateRanking(countryCode: CountryIsoCode) = {
        for {
          ranking ← analytics.getRanking(Option(countryCode), allCategories, params)
          summary ← rankingServices.updateRanking(CountryScope(countryCode), ranking)
        } yield summary
      }.value

      NineCardsService {
        countries
          .traverse[Free[F, ?], Result[UpdateRankingSummary]](generateRanking)
          .map(_.sequenceU)
      }
    }

    for {
      accessToken ← oauthServices.fetchAcessToken(serviceAccount)
      params = RankingParams(dateRange, rankingLength, AnalyticsToken(accessToken.value))
      countries            ← countryPersistence.getCountries(pageParams)
      countriesWithRanking ← analytics.getCountriesWithRanking(params)
      countriesCode     = countries.map(c ⇒ CountryIsoCode(c.isoCode2))
      selectedCountries = countriesCode.intersect(countriesWithRanking.countries)
      updateRankingsSummary ← generateRankings(selectedCountries, params)
    } yield
      Reload.SummaryResponse(
        countriesWithoutRanking = countriesCode diff selectedCountries,
        countriesWithRanking = updateRankingsSummary
      )
  }.value

  def reloadRankingByScope(
      scope: GeoScope,
      params: RankingParams): Free[F, Result[Reload.Response]] = {

    def generateRanking(scope: GeoScope, countries: List[CountryIsoCode]) = scope match {
      case WorldScope ⇒ analytics.getRanking(None, allCategories, params)
      case CountryScope(code) if hasRankingInfo(code, countries) ⇒
        analytics.getRanking(Option(code), allCategories, params)
      case _ ⇒
        NineCardsService.left[F, GoogleAnalyticsRanking](
          CountryNotFound("The country doesn't have ranking info"))
    }

    for {
      countriesWithRanking ← analytics.getCountriesWithRanking(params)
      ranking              ← generateRanking(scope, countriesWithRanking.countries)
      _                    ← rankingServices.updateRanking(scope, ranking)
    } yield Reload.Response()
  }.value

  def getRankedDeviceApps(
      location: Option[String],
      deviceApps: Map[String, List[Package]]
  ): Free[F, Result[List[RankedAppsByCategory]]] = {

    def unifyDeviceApps(deviceApps: Map[String, List[Package]]) = {
      val (games, otherApps) = deviceApps.partition { case (cat, _) ⇒ cat.matches("GAME\\_.*") }

      if (games.isEmpty)
        otherApps
      else
        otherApps.combine(Map("GAME" → games.flatMap { case (cat, apps) ⇒ apps }.toList))
    }

    if (deviceApps.isEmpty)
      NineCardsService.right(List.empty[RankedAppsByCategory]).value
    else {
      val unifiedDeviceApps = unifyDeviceApps(deviceApps)
      val unrankedApps = unifiedDeviceApps.flatMap {
        case (cat, apps) ⇒ apps map toUnrankedApp(cat)
      }.toSet

      for {
        geoScope ← location.fold(NineCardsService.right[F, GeoScope](WorldScope))(
          geoScopeFromLocation)
        rankedApps ← rankingServices.getRankingForApps(geoScope, unrankedApps)
        rankedAppsByCategory = rankedApps.groupBy(_.category)
        unrankedDeviceApps = unifiedDeviceApps map {
          case (category, apps) ⇒
            val appWithoutRanking = apps
              .diff(rankedAppsByCategory.getOrElse(category, Nil).map(_.packageName))
              .map(RankedApp(_, category, None))
            (category, appWithoutRanking)
        }
      } yield
        (rankedAppsByCategory combine unrankedDeviceApps)
          .map(toRankedAppsByCategory(limit = None))
          .toList
          .sortBy(r ⇒ Category.sortedValues.indexOf(r.category))
    }.value
  }

  def getRankedAppsByMoment(
      location: Option[String],
      deviceApps: List[Package],
      moments: List[String],
      limit: Int
  ): Free[F, Result[List[RankedAppsByCategory]]] = {
    if (deviceApps.isEmpty)
      NineCardsService.right(List.empty[RankedAppsByCategory]).value
    else {
      for {
        geoScope ← location.fold(NineCardsService.right[F, GeoScope](WorldScope))(
          geoScopeFromLocation)
        rankedApps ← rankingServices.getRankingForAppsWithinMoments(geoScope, deviceApps, moments)
      } yield
        rankedApps
          .groupBy(_.category)
          .map(toRankedAppsByCategory(limit = Option(limit)))
          .toList
    }.value
  }

  def getRankedWidgets(
      location: Option[String],
      apps: List[Package],
      moments: List[String],
      limit: Int
  ): Free[F, Result[List[RankedWidgetsByMoment]]] = {
    if (apps.isEmpty)
      NineCardsService.right(List.empty[RankedWidgetsByMoment]).value
    else {
      for {
        geoScope ← location.fold(NineCardsService.right[F, GeoScope](WorldScope))(
          geoScopeFromLocation)
        rankedWidgets ← rankingServices.getRankingForWidgets(
          geoScope,
          apps,
          moments map toWidgetMoment)
      } yield
        rankedWidgets
          .groupBy(_.moment)
          .map(toRankedWidgetsByMoment(limit))
          .toList
    }.value
  }

  private[this] def geoScopeFromLocation(isoCode: String): NineCardsService[F, GeoScope] =
    countryPersistence
      .getCountryByIsoCode2(isoCode.toUpperCase)
      .map { country ⇒
        val scope: GeoScope = CountryScope(CountryIsoCode(country.isoCode2))
        scope
      }
      .recover { case _: CountryNotFound ⇒ WorldScope }

  private[this] def hasRankingInfo(code: CountryIsoCode, countries: List[CountryIsoCode]) =
    countries.exists(_.value.equalsIgnoreCase(code.value))
}

object RankingProcesses {

  implicit def processes[F[_]](
      implicit analytics: GoogleAnalytics.Services[F],
      countryPersistence: algebra.Country.Services[F],
      oauthServices: algebra.GoogleOAuth.Services[F],
      rankingServices: algebra.Ranking.Services[F]
  ) = new RankingProcesses

}
