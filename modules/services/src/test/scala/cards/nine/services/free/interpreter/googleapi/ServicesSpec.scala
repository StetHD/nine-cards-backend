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

package cards.nine.services.free.interpreter.googleapi

import cards.nine.commons.NineCardsErrors.{NineCardsError, WrongGoogleAuthToken}
import cards.nine.commons.NineCardsService.Result
import cards.nine.commons.config.Domain.{GoogleApiConfiguration, GoogleApiTokenInfo}
import cards.nine.domain.account.GoogleIdToken
import cards.nine.services.free.domain.TokenInfo
import cards.nine.services.utils.MockServerService
import org.mockserver.model.HttpRequest._
import org.mockserver.model.HttpResponse._
import org.mockserver.model.HttpStatusCode._
import org.specs2.matcher.DisjunctionMatchers
import org.specs2.mutable.Specification

object GoogleApiServerResponse {

  val getTokenInfoValidResponse =
    """
      |{
      | "iss": "accounts.google.com",
      | "at_hash": "miKQC8jFj8FFAxDoK4HTSA",
      | "aud": "407408718192.apps.googleusercontent.com",
      | "sub": "106222693719864970737",
      | "email_verified": "true",
      | "azp": "407408718192.apps.googleusercontent.com",
      | "hd": "example.com",
      | "email": "user@example.com",
      | "iat": "1457605848",
      | "exp": "1457609448",
      | "alg": "RS256",
      | "kid": "eece476bc7e07fb1efec961e1ab277ebace0fe0f"
      |}
    """.stripMargin

  val getTokenInfoValidResponseWithoutHd =
    """
      |{
      | "iss": "accounts.google.com",
      | "at_hash": "miKQC8jFj8FFAxDoK4HTSA",
      | "aud": "407408718192.apps.googleusercontent.com",
      | "sub": "106222693719864970737",
      | "email_verified": "true",
      | "azp": "407408718192.apps.googleusercontent.com",
      | "email": "user@gmailcom",
      | "iat": "1457605848",
      | "exp": "1457609448",
      | "alg": "RS256",
      | "kid": "eece476bc7e07fb1efec961e1ab277ebace0fe0f"
      |}
    """.stripMargin

  val getTokenInfoWrongResponse =
    """
      |{
      | "error_description": "Invalid Value"
      |}
    """.stripMargin

}

trait MockGoogleApiServer extends MockServerService {

  import GoogleApiServerResponse._

  override val mockServerPort = 9999

  val getTokenInfoPath     = "/oauth2/v3/tokeninfo"
  val tokenIdParameterName = "id_token"

  val validTokenId   = "validTokenId"
  val otherTokenId   = "otherTokenId"
  val wrongTokenId   = "wrongTokenId"
  val failingTokenId = "failingTokenId"

  mockServer
    .when(
      request
        .withMethod("GET")
        .withPath(getTokenInfoPath)
        .withQueryStringParameter(tokenIdParameterName, validTokenId)
    )
    .respond(
      response
        .withStatusCode(OK_200.code)
        .withHeader(jsonHeader)
        .withBody(getTokenInfoValidResponse)
    )

  mockServer
    .when(
      request
        .withMethod("GET")
        .withPath(getTokenInfoPath)
        .withQueryStringParameter(tokenIdParameterName, otherTokenId)
    )
    .respond(
      response
        .withStatusCode(OK_200.code)
        .withHeader(jsonHeader)
        .withBody(getTokenInfoValidResponseWithoutHd)
    )

  mockServer
    .when(
      request
        .withMethod("GET")
        .withPath(getTokenInfoPath)
        .withQueryStringParameter(tokenIdParameterName, wrongTokenId)
    )
    .respond(
      response
        .withStatusCode(OK_200.code)
        .withHeader(jsonHeader)
        .withBody(getTokenInfoWrongResponse)
    )

  mockServer
    .when(
      request
        .withMethod("GET")
        .withPath(getTokenInfoPath)
        .withQueryStringParameter(tokenIdParameterName, failingTokenId)
    )
    .respond(
      response
        .withStatusCode(INTERNAL_SERVER_ERROR_500.code)
    )
}

class GoogleApiServicesSpec
    extends Specification
    with DisjunctionMatchers
    with MockGoogleApiServer {

  val config = GoogleApiConfiguration(
    protocol = "http",
    host = "localhost",
    port = Option(mockServerPort),
    tokenInfo = GoogleApiTokenInfo(
      path = getTokenInfoPath,
      tokenIdQueryParameter = tokenIdParameterName
    )
  )

  val googleApiServices = Services.services(config)

  "getTokenInfo" should {
    "return the TokenInfo object when a valid token id is provided" in {
      val response = googleApiServices.getTokenInfo(GoogleIdToken(validTokenId))

      response.unsafePerformSyncAttempt should be_\/-[Result[TokenInfo]].which { content ⇒
        content must beRight[TokenInfo]
      }
    }
    "return the TokenInfo object when a valid token id is provided and the hd field isn't" +
      "included into the response" in {
      val response = googleApiServices.getTokenInfo(GoogleIdToken(otherTokenId))

      response.unsafePerformSyncAttempt should be_\/-[Result[TokenInfo]].which { content ⇒
        content must beRight[TokenInfo]
      }
    }
    "return a WrongGoogleAuthToken error when a wrong token id is provided" in {
      val result = googleApiServices.getTokenInfo(GoogleIdToken(wrongTokenId))

      result.unsafePerformSyncAttempt should be_\/-[Result[TokenInfo]].which { content ⇒
        content must beLeft[NineCardsError](WrongGoogleAuthToken("Invalid Value"))
      }
    }
    "return an exception when something fails during the call to the Google API" in {
      val result = googleApiServices.getTokenInfo(GoogleIdToken(failingTokenId))

      result.unsafePerformSyncAttempt should be_-\/[Throwable]
    }
  }

}
