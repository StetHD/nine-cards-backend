package cards.nine.processes

import cards.nine.domain.account._
import cards.nine.processes.messages.InstallationsMessages._
import cards.nine.processes.messages.UserMessages.{ LoginRequest, LoginResponse }
import cards.nine.processes.App.NineCardsApp
import cards.nine.processes.utils.{ DummyNineCardsConfig, HashUtils }
import cards.nine.services.free.algebra
import cards.nine.services.free.domain.{ Installation, User }
import cats.Id
import cats.free.Free
import com.roundeights.hasher.Hasher
import io.freestyle.syntax._
import org.mockito.Matchers.{ eq ⇒ mockEq }
import org.specs2.ScalaCheck
import org.specs2.matcher.Matchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

trait UserProcessesSpecification
  extends Specification
  with Matchers
  with Mockito
  with UserProcessesContext
  with DummyNineCardsConfig
  with TestInterpreters {

  trait BasicScope extends Scope {
    implicit val userServices = mock[algebra.User.Services[NineCardsApp.T]]

    val userProcesses = UserProcesses.processes[NineCardsApp.T]
  }

  trait UserAndInstallationSuccessfulScope extends BasicScope {

    userServices.getByEmail(Email(mockEq(email))) returns Free.pure(Option(user))

    userServices.getInstallationByUserAndAndroidId(mockEq(userId), AndroidId(mockEq(androidId))) returns
      Free.pure(Option(installation))

    userServices.updateInstallation(mockEq(userId), mockEq(Option(DeviceToken(deviceToken))), AndroidId(mockEq(androidId))) returns
      Free.pure(installation)

    userServices.getBySessionToken(SessionToken(mockEq(sessionToken))) returns
      Free.pure(Option(user))

    userServices.getInstallationByUserAndAndroidId(mockEq(userId), AndroidId(mockEq(androidId))) returns
      Free.pure(Option(installation))
  }

  trait UserSuccessfulAndInstallationFailingScope extends BasicScope {

    userServices.getByEmail(Email(mockEq(email))) returns Free.pure(Option(user))

    userServices.getInstallationByUserAndAndroidId(mockEq(userId), AndroidId(mockEq(androidId))) returns
      Free.pure(nonExistingInstallation)

    userServices.addInstallation(mockEq(userId), mockEq(None), AndroidId(mockEq(androidId))) returns
      Free.pure(installation)

    userServices.getBySessionToken(SessionToken(mockEq(sessionToken))) returns
      Free.pure(Option(user))
  }

  trait UserAndInstallationFailingScope extends BasicScope {

    userServices.getByEmail(Email(mockEq(email))) returns Free.pure(nonExistingUser)

    userServices.add(Email(mockEq(email)), ApiKey(any[String]), SessionToken(any[String])) returns Free.pure(user)

    userServices.addInstallation(mockEq(userId), mockEq(None), AndroidId(mockEq(androidId))) returns
      Free.pure(installation)

    userServices.getBySessionToken(SessionToken(mockEq(sessionToken))) returns
      Free.pure(nonExistingUser)
  }

}

trait UserProcessesContext {

  val email = "valid.email@test.com"

  val userId = 1l

  val apiKey = "60b32e59-0d87-4705-a454-2e5b38bec13b"

  val wrongApiKey = "f93cff07-32c9-4995-8e80-a8adfafbf296"

  val sessionToken = "1d1afeea-c7ec-45d8-a6f8-825b836f2785"

  val banned = false

  val user = User(userId, Email(email), SessionToken(sessionToken), ApiKey(apiKey), banned)

  val nonExistingUser: Option[User] = None

  val androidId = "f07a13984f6d116a"

  val googleTokenId = "hd-w2tmEe7SZ_8vXhw_3f1iNnsrAqkpEvbPkFIo9oZeAq26u"

  val deviceToken = "abc"

  val installationId = 1l

  val loginRequest = LoginRequest(Email(email), AndroidId(androidId), SessionToken(sessionToken), GoogleIdToken(googleTokenId))

  val loginResponse = LoginResponse(ApiKey(apiKey), SessionToken(sessionToken))

  val updateInstallationRequest = UpdateInstallationRequest(userId, AndroidId(androidId), Option(DeviceToken(deviceToken)))

  val updateInstallationResponse = UpdateInstallationResponse(AndroidId(androidId), Option(DeviceToken(deviceToken)))

  val installation = Installation(installationId, userId, Option(DeviceToken(deviceToken)), AndroidId(androidId))

  val nonExistingInstallation: Option[Installation] = None

  val checkAuthTokenResponse = Option(userId)

  val dummyUrl = "http://localhost/dummy"

  val validAuthToken = Hasher(dummyUrl).hmac(apiKey).sha512.hex

  val wrongAuthToken = Hasher(dummyUrl).hmac(wrongApiKey).sha512.hex
}

class UserProcessesSpec
  extends UserProcessesSpecification
  with ScalaCheck {

  "signUpUser" should {
    "return LoginResponse object when the user exists and installation" in
      new UserAndInstallationSuccessfulScope {
        val signUpUser = userProcesses.signUpUser(loginRequest)
        signUpUser.exec[Id] shouldEqual loginResponse
      }

    "return LoginResponse object when the user exists but not installation" in
      new UserSuccessfulAndInstallationFailingScope {
        val signUpUser = userProcesses.signUpUser(loginRequest)
        signUpUser.exec[Id] shouldEqual loginResponse
      }

    "return LoginResponse object when there isn't user or installation" in
      new UserAndInstallationFailingScope {
        val signUpUser = userProcesses.signUpUser(loginRequest)
        signUpUser.exec[Id] shouldEqual loginResponse
      }
  }

  "updateInstallation" should {
    "return UpdateInstallationResponse object" in new UserAndInstallationSuccessfulScope {
      val signUpInstallation = userProcesses.updateInstallation(updateInstallationRequest)
      signUpInstallation.exec[Id] shouldEqual updateInstallationResponse
    }
  }

  "checkAuthToken" should {
    "return the userId if there is a user with the given sessionToken and androidId and the " +
      "auth token is valid" in new UserAndInstallationSuccessfulScope {
        val checkAuthToken = userProcesses.checkAuthToken(
          sessionToken = SessionToken(sessionToken),
          androidId    = AndroidId(androidId),
          authToken    = validAuthToken,
          requestUri   = dummyUrl
        )

        checkAuthToken.exec[Id] shouldEqual checkAuthTokenResponse
      }

    "return the userId for a valid sessionToken and androidId without considering the authToken " +
      "if the debug Mode is enabled" in new UserAndInstallationSuccessfulScope {

        val debugUserProcesses = UserProcesses.processes[NineCardsApp.T](
          userServices = userServices,
          config       = dummyConfig(debugMode = true),
          hashUtils    = HashUtils.hashUtils
        )

        val checkAuthToken = debugUserProcesses.checkAuthToken(
          sessionToken = SessionToken(sessionToken),
          androidId    = AndroidId(androidId),
          authToken    = "",
          requestUri   = dummyUrl
        )

        checkAuthToken.exec[Id] shouldEqual checkAuthTokenResponse
      }

    "return None when a wrong auth token is given" in new UserAndInstallationSuccessfulScope {
      val checkAuthToken = userProcesses.checkAuthToken(
        sessionToken = SessionToken(sessionToken),
        androidId    = AndroidId(androidId),
        authToken    = wrongAuthToken,
        requestUri   = dummyUrl
      )

      checkAuthToken.exec[Id] shouldEqual None
    }

    "return None if there is no user with the given sessionToken" in
      new UserAndInstallationFailingScope {
        val checkAuthToken = userProcesses.checkAuthToken(
          sessionToken = SessionToken(sessionToken),
          androidId    = AndroidId(androidId),
          authToken    = validAuthToken,
          requestUri   = dummyUrl
        )

        checkAuthToken.exec[Id] should beNone
      }

    "return None if there is no installation with the given androidId that belongs to the user" in
      new UserSuccessfulAndInstallationFailingScope {
        val checkAuthToken = userProcesses.checkAuthToken(
          sessionToken = SessionToken(sessionToken),
          androidId    = AndroidId(androidId),
          authToken    = validAuthToken,
          requestUri   = dummyUrl
        )

        checkAuthToken.exec[Id] should beNone
      }
  }
}
