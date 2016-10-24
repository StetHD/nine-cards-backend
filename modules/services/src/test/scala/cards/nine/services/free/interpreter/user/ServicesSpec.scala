package cards.nine.services.free.interpreter.user

import cards.nine.commons.NineCardsErrors._
import cards.nine.domain.account._
import cards.nine.domain.ScalaCheck._
import cards.nine.services.free.algebra
import cards.nine.services.free.algebra.User._
import cards.nine.services.free.domain.{ Installation, SharedCollection, SharedCollectionSubscription, User }
import cards.nine.services.free.interpreter.collection.Services.SharedCollectionData
import cards.nine.services.free.interpreter.user.Services.UserData
import cards.nine.services.persistence.NineCardsGenEntities.PublicIdentifier
import cards.nine.services.persistence.{ DomainDatabaseContext, NineCardsScalacheckGen }
import doobie.contrib.postgresql.pgtypes._
import org.specs2.ScalaCheck
import org.specs2.matcher.{ DisjunctionMatchers, MatchResult }
import org.specs2.mutable.Specification
import shapeless.syntax.std.product._

class ServicesSpec
  extends Specification
  with ScalaCheck
  with DomainDatabaseContext
  with DisjunctionMatchers
  with NineCardsScalacheckGen {

  object WithData {

    def apply[A](userData: UserData)(check: Long ⇒ MatchResult[A]) = {
      val id = {
        for {
          _ ← deleteAllRows
          id ← insertItem(User.Queries.insert, userData.toTuple)
        } yield id
      }.transactAndRun

      check(id)
    }

    def apply[A](userData: UserData, androidId: AndroidId)(check: (Long, Long) ⇒ MatchResult[A]) = {
      val (userId, installationId) = {
        for {
          _ ← deleteAllRows
          u ← insertItem(User.Queries.insert, userData.toTuple)
          i ← insertItem(Installation.Queries.insert, (u, emptyDeviceToken, androidId.value))
        } yield (u, i)
      }.transactAndRun

      check(userId, installationId)
    }

    def apply[A](
      userData: UserData,
      androidId: AndroidId,
      deviceToken: Option[DeviceToken]
    )(check: (Long, Long) ⇒ MatchResult[A]) = {
      val (userId, installationId) = {
        for {
          _ ← deleteAllRows
          u ← insertItem(User.Queries.insert, userData.toTuple)
          i ← insertItem(Installation.Queries.insert, (u, deviceToken, androidId.value))
        } yield (u, i)
      }.transactAndRun

      check(userId, installationId)
    }

    def apply[A](
      userData: UserData,
      androidId: AndroidId,
      deviceToken: DeviceToken,
      collectionData: SharedCollectionData
    )(check: ⇒ MatchResult[A]) = {
      {
        for {
          _ ← deleteAllRows
          u ← insertItem(User.Queries.insert, userData.toTuple)
          i ← insertItem(Installation.Queries.insert, (u, Option(deviceToken.value), androidId.value))
          c ← insertItem(SharedCollection.Queries.insert, collectionData.copy(userId = Option(u)).toTuple)
          _ ← insertItemWithoutGeneratedKeys(
            sql    = SharedCollectionSubscription.Queries.insert,
            values = (c, u, collectionData.publicIdentifier)
          )
        } yield (u, i, c)
      }.transactAndRun

      check
    }
  }

  def runService[A](op: algebra.User.Ops[A]) = userPersistenceServices.apply(op)

  sequential

  "addUser" should {
    "new users can be created" in {
      prop { userData: UserData ⇒
        WithEmptyDatabase {
          val insertedUser = runService(
            Add(
              email        = Email(userData.email),
              apiKey       = ApiKey(userData.apiKey),
              sessionToken = SessionToken(userData.sessionToken)
            )
          ).transactAndRun

          insertedUser must beRight[User].which { user ⇒
            user.email.value must_== userData.email
          }
        }
      }
    }
  }

  "getUserByEmail" should {
    "return an UserNotFound error if the table is empty" in {
      prop { (email: Email) ⇒

        WithEmptyDatabase {
          val user = runService(
            GetByEmail(email)
          ).transactAndRun

          user must beLeft(UserNotFound(s"User with email ${email.value} not found"))
        }
      }
    }
    "return an user if there is an user with the given email in the database" in {
      prop { userData: UserData ⇒

        WithData(userData) { id ⇒
          val user = runService(
            GetByEmail(Email(userData.email))
          ).transactAndRun

          user must beRight[User].which {
            user ⇒
              user.id must_== id
              user.apiKey.value must_== userData.apiKey
              user.email.value must_== userData.email
              user.sessionToken.value must_== userData.sessionToken
          }
        }
      }
    }
    "return an UserNotFound error if there isn't any user with the given email in the database" in {
      prop { userData: UserData ⇒

        WithData(userData) { _ ⇒
          val wrongEmail = Email(userData.email.reverse)

          val user = runService(
            GetByEmail(wrongEmail)
          ).transactAndRun

          user must beLeft(UserNotFound(s"User with email ${wrongEmail.value} not found"))
        }
      }
    }
  }

  "getUserBySessionToken" should {
    "return an UserNotFound error if the table is empty" in {
      prop { (email: Email, sessionToken: SessionToken) ⇒

        WithEmptyDatabase {
          val user = runService(
            GetBySessionToken(sessionToken)
          ).transactAndRun

          user must beLeft(UserNotFound(s"User with sessionToken ${sessionToken.value} not found"))
        }
      }
    }

    "return an user if there is an user with the given sessionToken in the database" in {
      prop { userData: UserData ⇒

        WithData(userData) { id ⇒
          val user = runService(
            GetBySessionToken(SessionToken(userData.sessionToken))
          ).transactAndRun

          user must beRight[User].which {
            user ⇒
              user.id must_== id
              user.apiKey.value must_== userData.apiKey
              user.email.value must_== userData.email
              user.sessionToken.value must_== userData.sessionToken
          }
        }
      }
    }

    "return an UserNotFound error if there isn't any user with the given sessionToken in the database" in {
      prop { userData: UserData ⇒

        WithData(userData) { _ ⇒
          val wrongSessionToken = SessionToken(userData.sessionToken.reverse)

          val user = runService(
            GetBySessionToken(wrongSessionToken)
          ).transactAndRun

          user must beLeft(UserNotFound(s"User with sessionToken ${wrongSessionToken.value} not found"))
        }
      }
    }
  }

  "createInstallation" should {
    "new installation can be created" in {
      prop { (androidId: AndroidId, userData: UserData) ⇒

        WithData(userData) { userId ⇒
          val insertedInstallation = runService(
            AddInstallation(
              user        = userId,
              deviceToken = None,
              androidId   = androidId
            )
          ).transactAndRun

          insertedInstallation must beRight[Installation].which { installation ⇒
            installation.userId must_== userId
            installation.deviceToken must_== None
            installation.androidId must_== androidId
          }
        }
      }
    }
  }

  "getInstallationByUserAndAndroidId" should {
    "return an InstallationNotFound error if the table is empty" in {
      prop { (androidId: AndroidId, userId: Long) ⇒

        WithEmptyDatabase {
          val installation = runService(
            GetInstallationByUserAndAndroidId(
              user      = userId,
              androidId = androidId
            )
          ).transactAndRun

          installation must beLeft(InstallationNotFound(s"Installation for android id ${androidId.value} not found"))
        }
      }
    }
    "installations can be queried by their userId and androidId" in {
      prop { (androidId: AndroidId, userData: UserData) ⇒

        WithData(userData, androidId) { (userId, installationId) ⇒

          val installation = runService(
            GetInstallationByUserAndAndroidId(
              user      = userId,
              androidId = androidId
            )
          ).transactAndRun

          installation must beRight[Installation].which {
            install ⇒ install.id must_== installationId
          }
        }
      }
    }
    "return an InstallationNotFound error if there isn't any installation with the given userId " +
      "and androidId in the database" in {
        prop { (androidId: AndroidId, userData: UserData) ⇒

          WithData(userData, androidId) { (userId, _) ⇒

            val wrongAndroidId = AndroidId(androidId.value.reverse)

            val installation = runService(
              GetInstallationByUserAndAndroidId(
                user      = userId,
                androidId = wrongAndroidId
              )
            ).transactAndRun

            installation must beLeft(InstallationNotFound(s"Installation for android id ${wrongAndroidId.value} not found"))
          }
        }
      }
  }

  "getSubscribedInstallationByCollection" should {
    "return an empty list if the table is empty" in {
      prop { (publicIdentifier: PublicIdentifier) ⇒

        WithEmptyDatabase {
          val installation = runService(
            GetSubscribedInstallationByCollection(
              collectionPublicId = publicIdentifier.value
            )
          ).transactAndRun

          installation must beRight[List[Installation]](Nil)
        }
      }
    }
    "return a list of installations that are subscribed to the collection" in {
      prop { (userData: UserData, collectionData: SharedCollectionData, androidId: AndroidId, deviceToken: DeviceToken) ⇒

        WithData(userData, androidId, deviceToken, collectionData) {
          val installation = runService(
            GetSubscribedInstallationByCollection(
              collectionPublicId = collectionData.publicIdentifier
            )
          ).transactAndRun

          installation must beRight[List[Installation]].which { list ⇒
            list must haveSize(be_>(0))
          }
        }
      }
    }
    "return an empty list if there is no installation subscribed to the collection" in {
      prop { (userData: UserData, collectionData: SharedCollectionData, androidId: AndroidId, deviceToken: DeviceToken) ⇒

        WithData(userData, androidId, deviceToken, collectionData) {
          val installation = runService(
            GetSubscribedInstallationByCollection(
              collectionPublicId = collectionData.publicIdentifier.reverse
            )
          ).transactAndRun

          installation must beRight[List[Installation]](Nil)
        }
      }
    }
  }

  "updateInstallation" should {
    "fail if the table is empty" in {
      prop { (userId: Long, androidId: AndroidId, deviceToken: Option[DeviceToken]) ⇒

        WithEmptyDatabase {
          val installation = runService(
            UpdateInstallation(
              user        = userId,
              deviceToken = deviceToken,
              androidId   = androidId
            )
          ).transactAndAttempt

          installation must be_-\/[Throwable]
        }
      }
    }
    "return the updated installation if there is an installation for the given user and android id" in {
      prop { (userData: UserData, androidId: AndroidId, deviceToken: Option[DeviceToken], newDeviceToken: Option[DeviceToken]) ⇒

        WithData(userData, androidId, deviceToken) { (userId, _) ⇒
          val updatedInstallation = runService(
            UpdateInstallation(
              user        = userId,
              deviceToken = newDeviceToken,
              androidId   = androidId
            )
          ).transactAndRun

          updatedInstallation must beRight[Installation].which { installation ⇒
            installation.deviceToken must_== newDeviceToken
          }
        }
      }
    }
    "fail if there isn't an installation for the given user and android id" in {
      prop { (userData: UserData, androidId: AndroidId, deviceToken: Option[DeviceToken], newDeviceToken: Option[DeviceToken]) ⇒

        WithData(userData, androidId, deviceToken) { (userId, _) ⇒
          val updatedInstallation = runService(
            UpdateInstallation(
              user        = userId,
              deviceToken = newDeviceToken,
              androidId   = AndroidId(androidId.value.reverse)
            )
          ).transactAndAttempt

          updatedInstallation must be_-\/[Throwable]
        }
      }
    }
  }
}
