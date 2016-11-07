package cards.nine.processes

import cats.data.Xor
import cats.free.Free
import cards.nine.domain.account._
import cards.nine.processes.App.NineCardsApp
import cards.nine.services.free.algebra.GoogleApi.Services
import cards.nine.services.free.domain.{ TokenInfo, WrongTokenInfo }
import cats.Id
import io.freestyle.syntax._
import org.specs2.ScalaCheck
import org.specs2.matcher.Matchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

trait GoogleApiProcessesSpecification
  extends Specification
  with Matchers
  with Mockito
  with GoogleApiProcessesContext
  with TestInterpreters {

  trait BasicScope extends Scope {

    implicit val googleApiServices: Services[NineCardsApp.T] = mock[Services[NineCardsApp.T]]
    val googleApiProcesses = new GoogleApiProcesses[NineCardsApp.T]

  }

  trait SuccessfulScope extends BasicScope {

    googleApiServices.getTokenInfo(GoogleIdToken(any[String])) returns Free.pure(Xor.right(tokenInfo))

  }

  trait UnsuccessfulScope extends BasicScope {

    googleApiServices.getTokenInfo(GoogleIdToken(any[String])) returns Free.pure(Xor.left(wrongTokenInfo))

  }

}

trait GoogleApiProcessesContext {

  val email = Email("valid.email@test.com")

  val wrongEmail = Email("wrong.email@test.com")

  val tokenId = GoogleIdToken("eyJhbGciOiJSUzI1NiIsImtpZCI6IjcxMjI3MjFlZWQwYjQ1YmUxNWUzMGI2YThhOThjOTM3ZTJlNmQxN")

  val tokenInfo = TokenInfo(
    email_verified = "true",
    email          = email.value
  )

  val wrongTokenInfo = WrongTokenInfo(error_description = "Invalid Value")
}

class GoogleApiProcessesSpec
  extends GoogleApiProcessesSpecification
  with ScalaCheck {

  "checkGoogleTokenId" should {
    "return true if the given tokenId is valid" in new SuccessfulScope {
      val tokenIdValidation = googleApiProcesses.checkGoogleTokenId(email, tokenId)

      tokenIdValidation.exec[Id] should beTrue
    }

    "return false if the given tokenId is valid but the given email address is different" in new SuccessfulScope {
      val tokenIdValidation = googleApiProcesses.checkGoogleTokenId(wrongEmail, tokenId)

      tokenIdValidation.exec[Id] should beFalse
    }

    "return false if the given tokenId is not valid" in new UnsuccessfulScope {
      val tokenIdValidation = googleApiProcesses.checkGoogleTokenId(email, tokenId)

      tokenIdValidation.exec[Id] should beFalse
    }
  }
}
