package cards.nine.services.free.algebra

import cards.nine.commons.NineCardsErrors.NineCardsError
import cards.nine.commons.NineCardsService
import cards.nine.commons.NineCardsService._
import cards.nine.services.free.domain
import cats.free.Free
import io.freestyle.free

object Country {

  @free trait Services[F[_]] {

    def getCountryByIsoCode2F(isoCode: String): Free[F, NineCardsError Either domain.Country]

    def getCountryByIsoCode2(isoCode: String): NineCardsService[F, domain.Country] =
      NineCardsService(getCountryByIsoCode2F(isoCode))
  }

}
