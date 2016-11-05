package cards.nine.services.free.interpreter.country

import cards.nine.commons.NineCardsErrors.CountryNotFound
import cards.nine.commons.NineCardsService.Result
import cards.nine.services.free.algebra.Country
import cards.nine.services.free.domain
import cards.nine.services.free.domain.Country.Queries
import cards.nine.services.persistence.Persistence
import cats.syntax.either._
import doobie.imports._

class Services(persistence: Persistence[domain.Country]) extends Country.Services.Interpreter[ConnectionIO] {

  def getCountryByIsoCode2(isoCode: String): ConnectionIO[Result[domain.Country]] =
    persistence.fetchOption(Queries.getByIsoCode2Sql, isoCode.toUpperCase) map {
      Either.fromOption(_, CountryNotFound(s"Country with ISO code2 $isoCode doesn't exist"))
    }

  def getCountryByIsoCode2FImpl(isoCode: String): ConnectionIO[Result[domain.Country]] =
    getCountryByIsoCode2(isoCode)
}

object Services {

  def services(implicit persistence: Persistence[domain.Country]) = new Services(persistence)
}
