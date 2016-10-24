package cards.nine.services.free.interpreter.country

import cards.nine.commons.NineCardsErrors.{ CountryNotFound, NineCardsError }
import cards.nine.domain.pagination.Page
import cards.nine.domain.ScalaCheck._
import cards.nine.services.free.algebra
import cards.nine.services.free.algebra.Country.{ GetCountries, GetCountryByIsoCode2 }
import cards.nine.services.free.domain.Country
import cards.nine.services.free.domain.Country.Queries
import cards.nine.services.persistence.NineCardsGenEntities.WrongIsoCode2
import cards.nine.services.persistence.{ DomainDatabaseContext, NineCardsScalacheckGen }
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification

class ServicesSpec
  extends Specification
  with ScalaCheck
  with DomainDatabaseContext
  with NineCardsScalacheckGen {

  def runService[A](op: algebra.Country.Ops[A]) = countryPersistenceServices.apply(op)

  sequential

  "getCountries" should {
    "return a list of countries whose size is less or equal to the page size" in {
      prop { pageParams: Page ⇒
        val countries = runService(
          GetCountries(pageParams)
        ).transactAndRun

        countries must beRight[List[Country]].which { list ⇒
          list must haveSize(be_<=(pageParams.pageSize.toInt))
        }
      }
    }
  }

  "getCountryByIsoCode2" should {
    "return a CountryNotFound error if a non-existing ISO code is provided" in {
      prop { isoCode: WrongIsoCode2 ⇒
        val error = CountryNotFound(s"Country with ISO code2 ${isoCode.value} doesn't exist")

        val country = runService(
          GetCountryByIsoCode2(isoCode.value)
        ).transactAndRun

        country must beLeft[NineCardsError](error)
      }
    }

    "return a country if a valid ISO code is provided" in {
      prop { index: Int ⇒

        val (searchedCountry, country) = {
          for {
            countries ← getItems[Country](Queries.getAllSql)
            searchedCountry = countries(Math.abs(index % countries.size))
            country ← runService(GetCountryByIsoCode2(searchedCountry.isoCode2))
          } yield (searchedCountry, country)
        }.transactAndRun

        country must beRight[Country].which { c ⇒
          c.isoCode2 must_== searchedCountry.isoCode2
          c.isoCode3 must_== searchedCountry.isoCode3
          c.name must_== searchedCountry.name
          c.continent must_== searchedCountry.continent
        }
      }
    }
  }
}
