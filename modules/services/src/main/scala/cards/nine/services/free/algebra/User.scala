package cards.nine.services.free.algebra

import cards.nine.domain.account._
import cards.nine.services.free.domain
import cats.free.Free
import io.freestyle.free

object User {

  @free trait Services[F[_]] {

    def add(email: Email, apiKey: ApiKey, sessionToken: SessionToken): Free[F, domain.User]

    def addInstallation(user: Long, deviceToken: Option[DeviceToken], androidId: AndroidId): Free[F, domain.Installation]

    def getByEmail(email: Email): Free[F, Option[domain.User]]

    def getBySessionToken(sessionToken: SessionToken): Free[F, Option[domain.User]]

    def getInstallationByUserAndAndroidId(user: Long, androidId: AndroidId): Free[F, Option[domain.Installation]]

    def getSubscribedInstallationByCollection(collectionPublicId: String): Free[F, List[domain.Installation]]

    def updateInstallation(user: Long, deviceToken: Option[DeviceToken], androidId: AndroidId): Free[F, domain.Installation]
  }

}
