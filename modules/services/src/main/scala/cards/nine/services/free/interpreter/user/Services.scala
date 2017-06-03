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
package cards.nine.services.free.interpreter.user

import cards.nine.commons.NineCardsService.Result
import cards.nine.commons.NineCardsErrors.{ InstallationNotFound, UserNotFound }
import cards.nine.domain.account._
import cards.nine.services.common.PersistenceService
import cards.nine.services.free.algebra.UserR._
import cards.nine.services.free.domain.Installation.{ Queries ⇒ InstallationQueries }
import cards.nine.services.free.domain.User.{ Queries ⇒ UserQueries }
import cards.nine.services.free.domain.{ Installation, User }
import cards.nine.services.persistence.Persistence
import doobie.imports.ConnectionIO

class Services(
  userPersistence: Persistence[User],
  installationPersistence: Persistence[Installation]
) extends Handler[ConnectionIO] {

  override def add(email: Email, apiKey: ApiKey, sessionToken: SessionToken): ConnectionIO[Result[User]] =
    PersistenceService.right(
      userPersistence.updateWithGeneratedKeys(
        sql    = UserQueries.insert,
        fields = User.allFields,
        values = (email, sessionToken, apiKey)
      )
    ).value

  override def getByEmail(email: Email): ConnectionIO[Result[User]] =
    PersistenceService.fromOptionF(
      userPersistence.fetchOption(UserQueries.getByEmail, email),
      UserNotFound(s"User with email ${email.value} not found")
    ).value

  override def getBySessionToken(sessionToken: SessionToken): ConnectionIO[Result[User]] =
    PersistenceService.fromOptionF(
      userPersistence.fetchOption(UserQueries.getBySessionToken, sessionToken),
      UserNotFound(s"User with sessionToken ${sessionToken.value} not found")
    ).value

  override def addInstallation(
    userId: Long,
    deviceToken: Option[DeviceToken],
    androidId: AndroidId
  ): ConnectionIO[Result[Installation]] =
    PersistenceService.right(
      installationPersistence.updateWithGeneratedKeys(
        sql    = InstallationQueries.insert,
        fields = Installation.allFields,
        values = (userId, deviceToken, androidId)
      )
    ).value

  def getInstallationByUserAndAndroidId(userId: Long, androidId: AndroidId): ConnectionIO[Result[Installation]] =
    PersistenceService.fromOptionF(
      installationPersistence.fetchOption(
        sql    = InstallationQueries.getByUserAndAndroidId,
        values = (userId, androidId)
      ),
      InstallationNotFound(s"Installation for android id ${androidId.value} not found")
    ).value

  def getSubscribedInstallationByCollection(publicIdentifier: String): ConnectionIO[Result[List[Installation]]] =
    PersistenceService.right(
      installationPersistence.fetchList(
        sql    = InstallationQueries.getSubscribedByCollection,
        values = publicIdentifier
      )
    ).value

  def updateInstallation(userId: Long, deviceToken: Option[DeviceToken], androidId: AndroidId): ConnectionIO[Result[Installation]] =
    PersistenceService.right(
      installationPersistence.updateWithGeneratedKeys(
        sql    = InstallationQueries.updateDeviceToken,
        fields = Installation.allFields,
        values = (deviceToken, userId, androidId)
      )
    ).value

}

object Services {

  def services(implicit userP: Persistence[User], installationP: Persistence[Installation]) =
    new Services(userP, installationP)
}