package cards.nine.services.free.algebra

import cards.nine.commons.NineCardsErrors.NineCardsError
import cards.nine.commons.NineCardsService
import cards.nine.commons.NineCardsService._
import cards.nine.domain.analytics.{ GeoScope, RankedApp, RankedWidget, UnrankedApp }
import cards.nine.domain.application.Package
import cards.nine.services.free.domain.Ranking._
import cats.free.Free
import io.freestyle.free

object Ranking {

  @free trait Services[F[_]] {

    def getRankingF(scope: GeoScope): Free[F, NineCardsError Either GoogleAnalyticsRanking]

    def getRankingForAppsF(
      scope: GeoScope,
      apps: Set[UnrankedApp]
    ): Free[F, NineCardsError Either List[RankedApp]]

    def getRankingForAppsWithinMomentsF(
      scope: GeoScope,
      apps: List[Package],
      moments: List[String]
    ): Free[F, NineCardsError Either List[RankedApp]]

    def getRankingForWidgetsF(
      scope: GeoScope,
      apps: List[Package],
      moments: List[String]
    ): Free[F, NineCardsError Either List[RankedWidget]]

    def updateRankingF(
      scope: GeoScope,
      ranking: GoogleAnalyticsRanking
    ): Free[F, NineCardsError Either UpdateRankingSummary]

    def getRanking(scope: GeoScope): NineCardsService[F, GoogleAnalyticsRanking] =
      NineCardsService(getRankingF(scope))

    def getRankingForApps(
      scope: GeoScope,
      apps: Set[UnrankedApp]
    ): NineCardsService[F, List[RankedApp]] =
      NineCardsService(getRankingForAppsF(scope, apps))

    def getRankingForAppsWithinMoments(
      scope: GeoScope,
      apps: List[Package],
      moments: List[String]
    ): NineCardsService[F, List[RankedApp]] =
      NineCardsService(getRankingForAppsWithinMomentsF(scope, apps, moments))

    def getRankingForWidgets(
      scope: GeoScope,
      apps: List[Package],
      moments: List[String]
    ): NineCardsService[F, List[RankedWidget]] =
      NineCardsService(getRankingForWidgetsF(scope, apps, moments))

    def updateRanking(
      scope: GeoScope,
      ranking: GoogleAnalyticsRanking
    ): NineCardsService[F, UpdateRankingSummary] =
      NineCardsService(updateRankingF(scope, ranking))
  }

}
