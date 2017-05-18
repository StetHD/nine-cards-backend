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

package cards.nine.api.accounts

import cards.nine.api.NineCardsHeaders.Domain._
import cards.nine.domain.account.{AndroidId, SessionToken}
import cards.nine.processes.account.messages._
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Shapeless._
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification

class ConvertersSpec extends Specification with ScalaCheck {

  import messages._

  val uuidGenerator: Gen[String] = Gen.uuid.map(_.toString)
  implicit val abSessionToken: Arbitrary[SessionToken] = Arbitrary(
    uuidGenerator.map(SessionToken.apply))

  "toLoginRequest" should {
    "convert an ApiLoginRequest to a LoginRequest object" in {
      prop { (apiRequest: ApiLoginRequest, sessionToken: SessionToken) ⇒
        val request = Converters.toLoginRequest(apiRequest, sessionToken)

        request.androidId must_== apiRequest.androidId
        request.email must_== apiRequest.email
        request.sessionToken must_== sessionToken
        request.tokenId must_== apiRequest.tokenId
      }
    }
  }

  "toApiLoginResponse" should {
    "convert a LoginResponse to an ApiLoginResponse object" in {
      prop { response: LoginResponse ⇒
        val apiLoginResponse = Converters.toApiLoginResponse(response)

        apiLoginResponse.sessionToken must_== response.sessionToken
      }
    }
  }

  "toUpdateInstallationRequest" should {
    "convert an ApiUpdateInstallationRequest to a UpdateInstallationRequest object" in {
      prop { (apiRequest: ApiUpdateInstallationRequest, userId: UserId, androidId: AndroidId) ⇒
        val userContext = UserContext(userId, androidId)

        val request = Converters.toUpdateInstallationRequest(apiRequest, userContext)

        request.androidId must_== androidId
        request.deviceToken must_== apiRequest.deviceToken
        request.userId must_== userId.value
      }
    }
  }

  "toApiUpdateInstallationResponse" should {
    "convert an UpdateInstallationResponse to an ApiUpdateInstallationResponse object" in {
      prop { (response: UpdateInstallationResponse) ⇒
        val apiResponse = Converters.toApiUpdateInstallationResponse(response)

        apiResponse.androidId must_== response.androidId
        apiResponse.deviceToken must_== response.deviceToken
      }
    }
  }

}
