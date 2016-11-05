package cards.nine.services.free.algebra

import cards.nine.commons.NineCardsErrors.NineCardsError
import cards.nine.commons.NineCardsService
import cards.nine.commons.NineCardsService.NineCardsService
import cards.nine.domain.analytics.{ CountryName, RankingParams }
import cards.nine.services.free.domain.Ranking.GoogleAnalyticsRanking
import cats.free.Free
import io.freestyle.free

object GoogleAnalytics {

  @free trait Services[F[_]] {

    def getRankingF(
      name: Option[CountryName],
      params: RankingParams
    ): Free[F, NineCardsError Either GoogleAnalyticsRanking]

    def getRanking(
      name: Option[CountryName],
      params: RankingParams
    ): NineCardsService[F, GoogleAnalyticsRanking] = NineCardsService(getRankingF(name, params))
  }

}