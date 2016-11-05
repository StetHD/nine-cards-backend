package cards.nine.services.free.interpreter.googleplay

import cards.nine.commons.TaskInstances._
import cards.nine.domain.application.{ FullCard, FullCardList, Package, PriceFilter }
import cards.nine.domain.market.MarketCredentials
import cards.nine.googleplay.processes.Wiring.GooglePlayApp
import cards.nine.googleplay.processes.{ CardsProcesses, Wiring }
import cards.nine.services.free.algebra.GooglePlay
import cats.data.Xor

import scalaz.concurrent.Task

class Services(implicit googlePlayProcesses: CardsProcesses[GooglePlayApp]) extends GooglePlay.Services.Interpreter[Task] {

  def resolveOne(packageName: Package, auth: MarketCredentials): Task[String Xor FullCard] = {
    googlePlayProcesses.getCard(packageName, auth)
      .foldMap(Wiring.interpreters).map {
        _.bimap(e ⇒ e.packageName.value, c ⇒ c)
      }
  }

  def resolveMany(
    packages: List[Package],
    auth: MarketCredentials,
    extendedInfo: Boolean
  ): Task[FullCardList] = {
    if (extendedInfo)
      googlePlayProcesses.getCards(packages, auth)
        .foldMap(Wiring.interpreters)
        .map(Converters.toFullCardList)
    else
      googlePlayProcesses.getBasicCards(packages, auth)
        .foldMap(Wiring.interpreters)
        .map(Converters.toFullCardList)
  }

  def recommendByCategory(
    category: String,
    filter: PriceFilter,
    excludedPackages: List[Package],
    limit: Int,
    auth: MarketCredentials
  ): Task[FullCardList] =
    googlePlayProcesses.recommendationsByCategory(
      Converters.toRecommendByCategoryRequest(category, filter, excludedPackages, limit),
      auth
    ).foldMap(Wiring.interpreters).flatMap {
        case Xor.Right(rec) ⇒ Task.delay(Converters.toRecommendations(rec))
        case Xor.Left(e) ⇒ Task.fail(new RuntimeException(e.message))
      }

  def recommendationsForApps(
    packageNames: List[Package],
    excludedPackages: List[Package],
    limitByApp: Int,
    limit: Int,
    auth: MarketCredentials
  ): Task[FullCardList] =
    googlePlayProcesses.recommendationsByApps(
      Converters.toRecommendByAppsRequest(packageNames, limitByApp, excludedPackages, limit),
      auth
    ).foldMap(Wiring.interpreters).map(Converters.toRecommendations)

  def searchApps(
    query: String,
    excludePackages: List[Package],
    limit: Int,
    auth: MarketCredentials
  ): Task[FullCardList] =
    googlePlayProcesses.searchApps(
      Converters.toSearchAppsRequest(query, excludePackages, limit),
      auth
    ).foldMap(Wiring.interpreters).map(Converters.toRecommendations)

  def recommendByCategoryImpl(
    category: String,
    priceFilter: PriceFilter,
    excludesPackages: List[Package],
    limit: Int, auth: MarketCredentials
  ): Task[FullCardList] =
    recommendByCategory(category, priceFilter, excludesPackages, limit, auth)

  def recommendationsForAppsImpl(
    packagesName: List[Package],
    excludesPackages: List[Package],
    limitPerApp: Int,
    limit: Int,
    auth: MarketCredentials
  ): Task[FullCardList] =
    recommendationsForApps(packagesName, excludesPackages, limitPerApp, limit, auth)

  def resolveImpl(
    packageName: Package,
    auth: MarketCredentials
  ): Task[String Xor FullCard] = resolveOne(packageName, auth)

  def resolveManyImpl(
    packageNames: List[Package],
    auth: MarketCredentials,
    extendedInfo: Boolean
  ): Task[FullCardList] = resolveMany(packageNames, auth, extendedInfo)

  def searchAppsImpl(
    query: String,
    excludesPackages: List[Package],
    limit: Int,
    auth: MarketCredentials
  ): Task[FullCardList] = searchApps(query, excludesPackages, limit, auth)

}

object Services {

  def services(implicit googlePlayProcesses: CardsProcesses[GooglePlayApp]) = new Services
}
