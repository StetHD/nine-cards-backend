package cards.nine.services.free.interpreter.googleapi

import cards.nine.commons.NineCardsErrors.{ NineCardsError, WrongGoogleAuthToken }
import cards.nine.commons.NineCardsService.Result
import cards.nine.commons.config.Domain.{ GoogleApiConfiguration, GoogleApiTokenInfo }
import cards.nine.domain.account.GoogleIdToken
import cards.nine.services.free.algebra.GoogleApi
import cards.nine.services.free.algebra.GoogleApi.GetTokenInfo
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

  val getTokenInfoPath = "/oauth2/v3/tokeninfo"
  val tokenIdParameterName = "id_token"

  val validTokenId = "eyJhbGciOiJSUzI1NiIsImtpZCI6ImVlY2U0NzZiYzdlMDdmYjFlZmVjOTYxZTFhYjI3N2ViYWN"
  val otherTokenId = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjZkNjQzY2Y5MGI1NTgyOTg0YjRlZTY3MjI4NGMzMzI0ZTg"
  val wrongTokenId = "eyJpc3MiOiJhY2NvdW50cy5nb29nbGUuY29tIiwiYXRfaGFzaCI6Im1pS1FDOGpGajhGRkF4RG9"
  val failingTokenId = "1c2VyY29udGVudC5jb20iLCJzdWIiOiIxMDYyMjI2OTM3MTk4NjQ5NzA3MzciLCJlbWFpbF92ZX"

  mockServer.when(
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

  mockServer.when(
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

  mockServer.when(
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

  mockServer.when(
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
    protocol  = "http",
    host      = "localhost",
    port      = Option(mockServerPort),
    tokenInfo = GoogleApiTokenInfo(
      path                  = getTokenInfoPath,
      tokenIdQueryParameter = tokenIdParameterName
    )
  )

  val googleApiServices = Services.services(config)

  def runService[A](op: GoogleApi.Ops[A]) = googleApiServices.apply(op)

  "getTokenInfo" should {
    "return the TokenInfo object when a valid token id is provided" in {
      val response = runService(GetTokenInfo(GoogleIdToken(validTokenId)))

      response.unsafePerformSyncAttempt should be_\/-[Result[TokenInfo]].which {
        content ⇒
          content must beRight[TokenInfo]
      }
    }
    "return the TokenInfo object when a valid token id is provided and the hd field isn't" +
      "included into the response" in {
        val response = runService(GetTokenInfo(GoogleIdToken(otherTokenId)))

        response.unsafePerformSyncAttempt should be_\/-[Result[TokenInfo]].which {
          content ⇒
            content must beRight[TokenInfo]
        }
      }
    "return a WrongGoogleAuthToken error when a wrong token id is provided" in {
      val result = runService(GetTokenInfo(GoogleIdToken(wrongTokenId)))

      result.unsafePerformSyncAttempt should be_\/-[Result[TokenInfo]].which {
        content ⇒
          content must beLeft[NineCardsError](WrongGoogleAuthToken("Invalid Value"))
      }
    }
    "return an exception when something fails during the call to the Google API" in {
      val result = runService(GetTokenInfo(GoogleIdToken(failingTokenId)))

      result.unsafePerformSyncAttempt should be_-\/[Throwable]
    }
  }

}
