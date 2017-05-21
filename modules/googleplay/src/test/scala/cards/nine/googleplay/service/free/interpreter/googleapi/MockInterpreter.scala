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
package cards.nine.googleplay.service.free.interpreter.googleapi

import cards.nine.domain.application.{ BasicCard, FullCard, Package }
import cards.nine.domain.market.MarketCredentials
import cards.nine.googleplay.domain._
import cards.nine.googleplay.domain.apigoogle.Failure
import cards.nine.googleplay.service.free.algebra.GoogleApi._

trait InterpreterServer[F[_]] {
  def getDetails(pack: Package, auth: MarketCredentials): F[Failure Either FullCard]
  def getDetailsList(packageNames: List[Package], marketAuth: MarketCredentials): F[List[Failure Either FullCard]]
  def getBulkDetails(packageNames: List[Package], marketAuth: MarketCredentials): F[Failure Either List[BasicCard]]
  def recommendationsByApps(request: RecommendByAppsRequest, auth: MarketCredentials): F[List[Package]]
  def recommendationsByCategory(request: RecommendByCategoryRequest, auth: MarketCredentials): F[InfoError Either List[Package]]
  def searchApps(request: SearchAppsRequest, auth: MarketCredentials): F[Failure Either List[Package]]
}

case class MockInterpreter[F[_]](server: InterpreterServer[F]) extends Handler[F] {
  def getDetails(pack: Package, auth: MarketCredentials) = server.getDetails(pack, auth)
  def getDetailsList(packs: List[Package], marketAuth: MarketCredentials) = server.getDetailsList(packs, marketAuth)
  def getBulkDetails(packs: List[Package], marketAuth: MarketCredentials) = server.getBulkDetails(packs, marketAuth)
  def recommendationsByApps(request: RecommendByAppsRequest, auth: MarketCredentials) = server.recommendationsByApps(request, auth)
  def recommendationsByCategory(request: RecommendByCategoryRequest, auth: MarketCredentials) = server.recommendationsByCategory(request, auth)
  def searchApps(request: SearchAppsRequest, auth: MarketCredentials) = server.searchApps(request, auth)
}

