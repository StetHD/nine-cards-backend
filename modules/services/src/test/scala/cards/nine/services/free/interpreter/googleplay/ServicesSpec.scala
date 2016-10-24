package cards.nine.services.free.interpreter.googleplay

import cards.nine.commons.NineCardsErrors.{ NineCardsError, PackageNotResolved }
import cards.nine.commons.NineCardsService.Result
import cards.nine.domain.account.AndroidId
import cards.nine.domain.application.{ BasicCard, CardList, Category, FullCard, Package, PriceFilter }
import cards.nine.domain.market.{ Localization, MarketCredentials, MarketToken }
import cards.nine.googleplay.domain._
import cards.nine.googleplay.processes.getcard.UnknownPackage
import cards.nine.googleplay.processes.{ CardsProcesses, Wiring }
import cards.nine.googleplay.processes.ResolveMany.{ Response ⇒ ResolveManyResponse }
import cards.nine.services.free.algebra.GooglePlay
import cards.nine.services.free.algebra.GooglePlay._
import cats.data.Xor
import cats.free.Free
import org.specs2.matcher.{ DisjunctionMatchers, Matchers, XorMatchers }
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class ServicesSpec
  extends Specification
  with Matchers
  with Mockito
  with DisjunctionMatchers
  with XorMatchers {

  import TestData._

  object TestData {

    def basicCardFor(packageName: String) = BasicCard(
      packageName = Package(packageName),
      title       = s"Title of $packageName",
      free        = false,
      icon        = s"Icon of $packageName",
      stars       = 5.0,
      downloads   = s"Downloads of $packageName"
    )

    def fullCardFor(packageName: String) = FullCard(
      packageName = Package(packageName),
      title       = s"Title of $packageName",
      free        = false,
      icon        = s"Icon of $packageName",
      stars       = 5.0,
      downloads   = s"Downloads of $packageName",
      categories  = List(s"Category 1 of $packageName", s"Category 2 of $packageName"),
      screenshots = List(s"Screenshot 1 of $packageName", s"Screenshot 2 of $packageName")
    )

    val packagesName = List("com.package.one", "com.package.two", "com.package.three", "com.package.four")
    val onePackageName = packagesName.head

    val packages = packagesName map Package
    val onePackage = Package(onePackageName)

    val (validPackagesName, wrongPackagesName) = packagesName.partition(_.length <= 15)

    val validPackages = validPackagesName map Package
    val wrongPackages = wrongPackagesName map Package

    val basicCards = validPackagesName map basicCardFor
    val fullCards = validPackagesName map fullCardFor

    val category = "SHOPPING"

    val limit = 20

    val numPerApp = 25

    val priceFilter = PriceFilter.FREE

    val word = "calendar"

    object AuthData {
      val androidId = "12345"
      val localization = "en_GB"
      val token = "m52_9876"

      val marketAuth = MarketCredentials(
        AndroidId(androidId),
        MarketToken(token),
        Some(Localization(localization))
      )

    }

    object Requests {
      val recommendByAppsRequest = RecommendByAppsRequest(
        searchByApps = packages,
        numPerApp    = numPerApp,
        excludedApps = wrongPackages,
        maxTotal     = limit
      )

      val recommendByCategoryRequest = RecommendByCategoryRequest(
        category     = Category.SHOPPING,
        priceFilter  = PriceFilter.FREE,
        excludedApps = wrongPackages,
        maxTotal     = limit
      )

      val searchAppsRequest = SearchAppsRequest(
        word         = word,
        excludedApps = wrongPackages,
        maxTotal     = limit
      )
    }

    object GooglePlayResponses {
      val fullCard = fullCardFor(onePackageName)
      val unknwonPackageError = UnknownPackage(onePackage)

      val recommendationsInfoError = InfoError("Something went wrong!")

      val basicCardList = CardList[BasicCard](
        missing = wrongPackages,
        cards   = basicCards
      )

      val fullCardList = CardList[FullCard](
        missing = wrongPackages,
        cards   = fullCards
      )

      val basicResolveManyResponse = ResolveManyResponse(wrongPackages, Nil, basicCards)

      val resolveManyResponse = ResolveManyResponse(wrongPackages, Nil, fullCards)
    }

  }

  trait BasicScope extends Scope {
    implicit val googlePlayProcesses = mock[CardsProcesses[Wiring.GooglePlayApp]]
    val services = Services.services

    def runService[A](op: GooglePlay.Ops[A]) = services.apply(op)
  }

  "resolveOne" should {
    "return the App object when a valid package name is provided" in new BasicScope {

      googlePlayProcesses.getCard(onePackage, AuthData.marketAuth) returns
        Free.pure(Xor.right(GooglePlayResponses.fullCard))

      val response = runService(Resolve(onePackage, AuthData.marketAuth))

      response.unsafePerformSyncAttempt must be_\/-[Result[FullCard]].which {
        content ⇒ content must beRight[FullCard](GooglePlayResponses.fullCard)
      }
    }

    "return an error message when a wrong package name is provided" in new BasicScope {

      googlePlayProcesses.getCard(onePackage, AuthData.marketAuth) returns
        Free.pure(Xor.left(GooglePlayResponses.unknwonPackageError))

      val response = runService(Resolve(onePackage, AuthData.marketAuth))

      response.unsafePerformSyncAttempt must be_\/-[Result[FullCard]].which {
        content ⇒ content must beLeft(PackageNotResolved(onePackageName))
      }
    }
  }

  "resolveMany" should {
    "return extended Google Play info for valid apps and a list of apps that are wrong" +
      "if extendedInfo flag is true" in new BasicScope {

        googlePlayProcesses.getCards(packages, AuthData.marketAuth) returns
          Free.pure(GooglePlayResponses.resolveManyResponse)

        val response = runService(ResolveManyDetailed(packages, AuthData.marketAuth))

        response.unsafePerformSyncAttempt must be_\/-[Result[CardList[FullCard]]].which { response ⇒
          response must beRight[CardList[FullCard]].which { appsInfo ⇒
            appsInfo.missing must containTheSameElementsAs(wrongPackages)
            appsInfo.cards must containTheSameElementsAs(fullCards)
          }
        }
      }

    "return basic Google Play info for valid apps and a list of apps that are wrong" +
      "if extendedInfo flag is false" in new BasicScope {

        googlePlayProcesses.getBasicCards(packages, AuthData.marketAuth) returns
          Free.pure(GooglePlayResponses.basicResolveManyResponse)

        val response = runService(ResolveManyBasic(packages, AuthData.marketAuth))

        response.unsafePerformSyncAttempt must be_\/-[Result[CardList[BasicCard]]].which { response ⇒
          response must beRight[CardList[BasicCard]].which { appsInfo ⇒
            appsInfo.missing must containTheSameElementsAs(wrongPackages)
            appsInfo.cards must containTheSameElementsAs(basicCards)
          }
        }
      }
  }

  "recommendByCategory" should {
    "return a list of free recommended apps for the given category" in new BasicScope {

      googlePlayProcesses.recommendationsByCategory(
        Requests.recommendByCategoryRequest,
        AuthData.marketAuth
      ) returns Free.pure(Xor.right(GooglePlayResponses.fullCardList))

      val response = runService(
        RecommendationsByCategory(
          category         = category,
          priceFilter      = priceFilter,
          excludedPackages = wrongPackages,
          limit            = limit,
          auth             = AuthData.marketAuth
        )
      )

      response.unsafePerformSyncAttempt must be_\/-[Result[CardList[FullCard]]].which { response ⇒
        response must beRight[CardList[FullCard]].which { rec ⇒
          rec.cards must containTheSameElementsAs(fullCards)
        }
      }
    }

    "return a RecommendationsServerError if something went wrong while getting recommendations" in new BasicScope {

      googlePlayProcesses.recommendationsByCategory(
        Requests.recommendByCategoryRequest,
        AuthData.marketAuth
      ) returns Free.pure(Xor.left(GooglePlayResponses.recommendationsInfoError))

      val response = runService(
        RecommendationsByCategory(
          category         = category,
          priceFilter      = priceFilter,
          excludedPackages = wrongPackages,
          limit            = limit,
          auth             = AuthData.marketAuth
        )
      )

      response.unsafePerformSyncAttempt must be_\/-[Result[CardList[FullCard]]].which { response ⇒
        response must beLeft[NineCardsError]
      }
    }
  }

  "recommendationsForApps" should {
    "return a list of recommended apps for the given list of packages" in new BasicScope {
      googlePlayProcesses.recommendationsByApps(
        Requests.recommendByAppsRequest,
        AuthData.marketAuth
      ) returns Free.pure(GooglePlayResponses.fullCardList)

      val response = runService(
        RecommendationsForApps(
          packagesName     = packages,
          excludedPackages = wrongPackages,
          limitPerApp      = numPerApp,
          limit            = limit,
          auth             = AuthData.marketAuth
        )
      )

      response.unsafePerformSyncAttempt must be_\/-[Result[CardList[FullCard]]].which { response ⇒
        response must beRight[CardList[FullCard]].which { rec ⇒
          rec.cards must containTheSameElementsAs(fullCards)
        }
      }
    }
  }

  "searchApps" should {
    "return a list of recommended apps for the given list of packages" in new BasicScope {
      googlePlayProcesses.searchApps(
        Requests.searchAppsRequest,
        AuthData.marketAuth
      ) returns Free.pure(GooglePlayResponses.basicCardList)

      val response = runService(
        SearchApps(
          query            = word,
          excludedPackages = wrongPackages,
          limit            = limit,
          auth             = AuthData.marketAuth
        )
      )

      response.unsafePerformSyncAttempt must be_\/-[Result[CardList[BasicCard]]].which { response ⇒
        response must beRight[CardList[BasicCard]].which { rec ⇒
          rec.cards must containTheSameElementsAs(basicCards)
        }
      }
    }
  }
}
