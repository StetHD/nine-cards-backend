package cards.nine.services.free.interpreter.user

import cards.nine.domain.account._
import cards.nine.services.free.algebra.User
import cards.nine.services.free.domain
import cards.nine.services.free.domain.Installation
import cards.nine.services.free.domain.Installation.{ Queries ⇒ InstallationQueries }
import cards.nine.services.free.domain.User.{ Queries ⇒ UserQueries }
import cards.nine.services.persistence.Persistence
import doobie.imports._

class Services(
  userPersistence: Persistence[domain.User],
  installationPersistence: Persistence[Installation]
) extends User.Services.Interpreter[ConnectionIO] {

  def addUser[K: Composite](email: Email, apiKey: ApiKey, sessionToken: SessionToken): ConnectionIO[K] =
    userPersistence.updateWithGeneratedKeys[K](
      sql    = UserQueries.insert,
      fields = domain.User.allFields,
      values = (email, sessionToken, apiKey)
    )

  def getUserByEmail(email: Email): ConnectionIO[Option[domain.User]] =
    userPersistence.fetchOption(UserQueries.getByEmail, email)

  def getUserBySessionToken(sessionToken: SessionToken): ConnectionIO[Option[domain.User]] =
    userPersistence.fetchOption(UserQueries.getBySessionToken, sessionToken)

  def createInstallation[K: Composite](
    userId: Long,
    deviceToken: Option[DeviceToken],
    androidId: AndroidId
  ): ConnectionIO[K] =
    userPersistence.updateWithGeneratedKeys[K](
      sql    = InstallationQueries.insert,
      fields = Installation.allFields,
      values = (userId, deviceToken, androidId)
    )

  def getInstallationByUserAndAndroidId(
    userId: Long,
    androidId: AndroidId
  ): ConnectionIO[Option[Installation]] =
    installationPersistence.fetchOption(
      sql    = InstallationQueries.getByUserAndAndroidId,
      values = (userId, androidId)
    )

  def getSubscribedInstallationByCollection(publicIdentifier: String): ConnectionIO[List[Installation]] =
    installationPersistence.fetchList(InstallationQueries.getSubscribedByCollection, publicIdentifier)

  def updateInstallation[K: Composite](userId: Long, deviceToken: Option[DeviceToken], androidId: AndroidId): ConnectionIO[K] =
    userPersistence.updateWithGeneratedKeys[K](
      sql    = InstallationQueries.updateDeviceToken,
      fields = Installation.allFields,
      values = (deviceToken, userId, androidId)
    )

  def addImpl(
    email: Email,
    apiKey: ApiKey,
    sessionToken: SessionToken
  ): ConnectionIO[domain.User] =
    addUser[domain.User](email, apiKey, sessionToken)

  def addInstallationImpl(
    user: Long,
    deviceToken: Option[DeviceToken],
    androidId: AndroidId
  ): ConnectionIO[Installation] =
    createInstallation[Installation](user, deviceToken, androidId)

  def getByEmailImpl(email: Email): ConnectionIO[Option[domain.User]] = getUserByEmail(email)

  def getBySessionTokenImpl(
    sessionToken: SessionToken
  ): ConnectionIO[Option[domain.User]] =
    getUserBySessionToken(sessionToken)

  def getInstallationByUserAndAndroidIdImpl(
    user: Long,
    androidId: AndroidId
  ): ConnectionIO[Option[Installation]] =
    getInstallationByUserAndAndroidId(user, androidId)

  def getSubscribedInstallationByCollectionImpl(
    collectionPublicId: String
  ): ConnectionIO[List[Installation]] =
    getSubscribedInstallationByCollection(collectionPublicId)

  def updateInstallationImpl(
    user: Long,
    deviceToken: Option[DeviceToken],
    androidId: AndroidId
  ): ConnectionIO[Installation] =
    updateInstallation[Installation](user, deviceToken, androidId)

}

object Services {

  case class UserData(
    email: String,
    apiKey: String,
    sessionToken: String
  )

  def services(
    implicit
    userPersistence: Persistence[domain.User],
    installationPersistence: Persistence[Installation]
  ) =
    new Services(userPersistence, installationPersistence)
}