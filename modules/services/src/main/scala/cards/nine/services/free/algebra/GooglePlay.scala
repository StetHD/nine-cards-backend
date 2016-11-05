package cards.nine.services.free.algebra

import cards.nine.domain.application.{ FullCard, FullCardList, Package, PriceFilter }
import cards.nine.domain.market.MarketCredentials
import cats.data.Xor
import cats.free.Free
import io.freestyle.free

object GooglePlay {

  @free trait Services[F[_]] {

    def resolve(packageName: Package, auth: MarketCredentials): Free[F, String Xor FullCard]

    def resolveMany(packageNames: List[Package], auth: MarketCredentials, extendedInfo: Boolean): Free[F, FullCardList]

    def recommendByCategory(
      category: String,
      priceFilter: PriceFilter,
      excludesPackages: List[Package],
      limit: Int,
      auth: MarketCredentials
    ): Free[F, FullCardList]

    def recommendationsForApps(
      packagesName: List[Package],
      excludesPackages: List[Package],
      limitPerApp: Int,
      limit: Int,
      auth: MarketCredentials
    ): Free[F, FullCardList]

    def searchApps(query: String, excludesPackages: List[Package], limit: Int, auth: MarketCredentials): Free[F, FullCardList]

  }

}

