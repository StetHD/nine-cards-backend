package cards.nine.services.free.domain

import cards.nine.domain.analytics.ReportType.AppsRankingByCategory
import cards.nine.domain.analytics.{ CountryIsoCode, CountryScope, WorldScope }
import cards.nine.services.free.domain.Ranking.CacheKey
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification

class CacheKeySpec
  extends Specification
  with ScalaCheck {

  "CacheKey.worldScope" should {
    "return a CacheKey val for World scope" in {
      CacheKey.worldScope must beLike {
        case key: CacheKey ⇒
          key.scope must_== WorldScope
          key.reportType must_== AppsRankingByCategory
      }
    }
  }

  "CacheKey.countryScope" should {
    "return a CacheKey val for the specified country" in {
      prop { (countryCode: String) ⇒
        CacheKey.countryScope(countryCode) must beLike {
          case key ⇒
            key.scope must_== CountryScope(CountryIsoCode(countryCode.toLowerCase))
            key.reportType must_== AppsRankingByCategory
        }
      }
    }
  }
}
