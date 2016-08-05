package com.fortysevendeg.ninecards.services.persistence

import com.fortysevendeg.ninecards.services.free.domain._
import com.fortysevendeg.ninecards.services.persistence.SharedCollectionPersistenceServices.SharedCollectionData
import com.fortysevendeg.ninecards.services.persistence.UserPersistenceServices.UserData
import doobie.imports._
import org.specs2.ScalaCheck
import org.specs2.matcher.DisjunctionMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeEach
import scala.annotation.tailrec
import scala.collection.immutable.List

import scalaz.syntax.traverse.ToTraverseOps // F[A] => TraverseOps[F, A]
import scalaz.std.list.listInstance // Traverse[List]

import shapeless.syntax.std.product._

trait SharedCollectionPersistenceServicesContext extends DomainDatabaseContext {

  val communicationCategory = "COMMUNICATION"

  val limit = 25

  val offset = 0

  val socialCategory = "SOCIAL"

  val deleteSharedCollectionsQuery = "DELETE FROM sharedcollections"

  val deleteUsersQuery = "DELETE FROM users"

  def createCollectionWithUser(
    collectionData: SharedCollectionData,
    userId: Option[Long]
  ): ConnectionIO[Long] =
    insertItem(SharedCollection.Queries.insert, collectionData.copy(userId = userId))

  def createCollectionsWithCategoryAndUser(
    collectionsData: List[SharedCollectionData],
    category: String,
    userId: Option[Long]
  ): ConnectionIO[Int] =
    insertItems(
      sql    = SharedCollection.Queries.insert,
      values = collectionsData.map(_.copy(category = category, userId = userId))
    )

  def createPackages(collectionId: Long, packageNames: List[String]): ConnectionIO[Int] =
    insertItems(SharedCollectionPackage.Queries.insert, packageNames map { (collectionId, _) })

  def createUser(userData: UserData): ConnectionIO[Long] =
    insertItem(User.Queries.insert, userData.toTuple)

  def deleteSharedCollections: ConnectionIO[Int] = deleteItems(deleteSharedCollectionsQuery)

  def deleteUsers: ConnectionIO[Int] = deleteItems(deleteUsersQuery)

  def divideList[A](n: Int, list: List[A]): List[List[A]] = {
    def merge(heads: List[A], tails: List[List[A]]): List[List[A]] = (heads, tails) match {
      case (Nil, Nil) ⇒ Nil
      case (h :: hs, Nil) ⇒ throw new Exception("This should not happen")
      case (Nil, t :: ts) ⇒ tails
      case (h :: hs, t :: ts) ⇒ (h :: t) :: merge(hs, ts)
    }

    @tailrec
    def divideAux(xs: List[A], results: List[List[A]]): List[List[A]] =
      if (xs.isEmpty)
        results map (_.reverse)
      else {
        val (pre, post) = xs.splitAt(n)
        divideAux(post, merge(pre, results))
      }

    divideAux(list, List.fill(n)(Nil))
  }

}

class SharedCollectionPersistenceServicesSpec
  extends Specification
  with BeforeEach
  with ScalaCheck
  with DisjunctionMatchers
  with NineCardsScalacheckGen
  with SharedCollectionPersistenceServicesContext {

  sequential

  def before = {
    flywaydb.clean()
    flywaydb.migrate()
  }

  "addCollection" should {
    "create a new shared collection when an existing user id is given" in {
      prop { (userData: UserData, collectionData: SharedCollectionData) ⇒
        val id = (for {
          u ← createUser(userData)
          c ← collectionPersistenceServices.addCollection[Long](
            collectionData.copy(userId = Option(u))
          )
        } yield c).transact(transactor).run

        val storedCollection = collectionPersistenceServices.getCollectionById(
          id = id
        ).transact(transactor).run

        storedCollection must beSome[SharedCollection].which {
          collection ⇒ collection.publicIdentifier must_== collectionData.publicIdentifier
        }
      }
    }

    "create a new shared collection without a defined user id" in {
      prop { (collectionData: SharedCollectionData) ⇒
        val id: Long = collectionPersistenceServices.addCollection[Long](
          collectionData.copy(userId = None)
        ).transact(transactor).run

        val storedCollection = collectionPersistenceServices.getCollectionById(
          id = id
        ).transact(transactor).run

        storedCollection must beSome[SharedCollection].which {
          collection ⇒ collection.publicIdentifier must_== collectionData.publicIdentifier
        }
      }
    }
  }

  "getCollectionById" should {
    "return None if the table is empty" in {
      prop { (id: Long) ⇒
        val collection = collectionPersistenceServices.getCollectionById(
          id = id
        ).transact(transactor).run

        collection must beNone
      }
    }
    "return a collection if there is a record with the given id in the database" in {
      prop { (userData: UserData, collectionData: SharedCollectionData) ⇒
        val id = (for {
          u ← insertItem(User.Queries.insert, userData.toTuple)
          c ← insertItem(SharedCollection.Queries.insert, collectionData.copy(userId = Option(u)).toTuple)
        } yield c).transact(transactor).run

        val storedCollection = collectionPersistenceServices.getCollectionById(
          id = id
        ).transact(transactor).run

        storedCollection must beSome[SharedCollection].which {
          collection ⇒ collection.publicIdentifier must_== collectionData.publicIdentifier
        }
      }
    }
    "return None if there isn't any collection with the given id in the database" in {
      prop { (userData: UserData, collectionData: SharedCollectionData) ⇒
        val id = (for {
          u ← insertItem(User.Queries.insert, userData.toTuple)
          c ← insertItem(SharedCollection.Queries.insert, collectionData.copy(userId = Option(u)).toTuple)
        } yield c).transact(transactor).run

        val storedCollection = collectionPersistenceServices.getCollectionById(
          id = id + 1000000
        ).transact(transactor).run

        storedCollection must beNone
      }
    }
  }

  "getCollectionByPublicIdentifier" should {
    "return None if the table is empty" in {
      prop { (publicIdentifier: String) ⇒

        val collection = collectionPersistenceServices.getCollectionByPublicIdentifier(
          publicIdentifier = publicIdentifier
        ).transact(transactor).run

        collection must beNone
      }
    }
    "return a collection if there is a record with the given public identifier in the database" in {
      prop { (userData: UserData, collectionData: SharedCollectionData) ⇒
        val id = (for {
          u ← insertItem(User.Queries.insert, userData.toTuple)
          c ← insertItem(SharedCollection.Queries.insert, collectionData.copy(userId = Option(u)).toTuple)
        } yield c).transact(transactor).run

        val storedCollection = collectionPersistenceServices.getCollectionByPublicIdentifier(
          publicIdentifier = collectionData.publicIdentifier
        ).transact(transactor).run

        storedCollection must beSome[SharedCollection].which {
          collection ⇒
            collection.id must_== id
            collection.publicIdentifier must_== collectionData.publicIdentifier
        }
      }
    }
    "return None if there isn't any collection with the given public identifier in the database" in {
      prop { (userData: UserData, collectionData: SharedCollectionData) ⇒
        val id = (for {
          u ← insertItem(User.Queries.insert, userData.toTuple)
          c ← insertItem(SharedCollection.Queries.insert, collectionData.copy(userId = Option(u)).toTuple)
        } yield c).transact(transactor).run

        val collection = collectionPersistenceServices.getCollectionByPublicIdentifier(
          publicIdentifier = collectionData.publicIdentifier.reverse
        ).transact(transactor).run

        collection must beNone
      }
    }
  }

  "getCollectionsByUserId" should {

    "return the List of Collections created by the User" in {

      prop { (ownerData: UserData, otherData: UserData, collectionData: List[SharedCollectionData]) ⇒
        val List(ownedData, disownedData, foreignData) = divideList[SharedCollectionData](3, collectionData)

        val setupTrans = for {
          ownerId ← createUser(ownerData)
          otherId ← createUser(otherData)
          owned ← ownedData traverse (createCollectionWithUser(_, Option(ownerId)))
          foreign ← foreignData traverse (createCollectionWithUser(_, Option(otherId)))
          disowned ← disownedData traverse (createCollectionWithUser(_, None))
        } yield (ownerId, owned)

        val (ownerId, owned) = setupTrans.transact(transactor).run

        val response: List[SharedCollection] =
          collectionPersistenceServices
            .getCollectionsByUserId(ownerId)
            .transact(transactor).run

        (response map (_.id)) must containTheSameElementsAs(owned)
      }
    }
  }

  "getLatestCollectionsByCategory" should {

    "return an empty list of collections if the tabls is empty" in {
      prop { category: String ⇒

        val response: List[SharedCollection] =
          collectionPersistenceServices
            .getLatestCollectionsByCategory(category, offset, limit)
            .transact(transactor).run

        response must beEmpty
      }
    }

    "return an empty list of collections if there are no records with the given category" in {
      prop { (userData: UserData, collectionsData: List[SharedCollectionData]) ⇒

        val setupTrans = for {
          userId ← createUser(userData)
          collections ← createCollectionsWithCategoryAndUser(
            collectionsData = collectionsData,
            category        = socialCategory,
            userId          = Option(userId)
          )
        } yield (userId, collections)

        val (userId, collections) = setupTrans.transact(transactor).run

        val response: List[SharedCollection] =
          collectionPersistenceServices
            .getLatestCollectionsByCategory(communicationCategory, offset, limit)
            .transact(transactor).run

        response must beEmpty
      }
    }

    "return a list of latest collections for the given category" in {
      prop { (userData: UserData, collectionsData: List[SharedCollectionData]) ⇒

        val List(socialCollections, otherCollections) = divideList(2, collectionsData)

        val setupTrans = for {
          userId ← createUser(userData)
          socialCollections ← createCollectionsWithCategoryAndUser(
            collectionsData = socialCollections,
            category        = socialCategory,
            userId          = Option(userId)
          )
          otherCollections ← createCollectionsWithCategoryAndUser(
            collectionsData = otherCollections,
            category        = communicationCategory,
            userId          = Option(userId)
          )
        } yield Unit

        setupTrans.transact(transactor).run

        val response = for {
          response ← collectionPersistenceServices.getLatestCollectionsByCategory(
            category = socialCategory,
            offset   = offset,
            limit    = limit
          )
          _ ← deleteSharedCollections
        } yield response

        val collections: List[SharedCollection] = response.transact(transactor).run

        val sortedSocialCollections = socialCollections.sortWith(_.publishedOn.getTime > _.publishedOn.getTime)

        collections.size must be_<=(limit)
        collections.headOption.map(_.publicIdentifier) must_== sortedSocialCollections.headOption.map(_.publicIdentifier)
      }
    }
  }

  "getTopCollectionsByCategory" should {

    "return an empty list of collections if the tabls is empty" in {
      prop { i: Int ⇒

        val response: List[SharedCollection] =
          collectionPersistenceServices
            .getLatestCollectionsByCategory(socialCategory, offset, limit)
            .transact(transactor).run

        response must beEmpty
      }
    }

    "return an empty list of collections if there are no records with the given category" in {
      prop { (userData: UserData, collectionsData: List[SharedCollectionData]) ⇒

        val setupTrans = for {
          userId ← createUser(userData)
          collections ← createCollectionsWithCategoryAndUser(collectionsData, socialCategory, Option(userId))
        } yield Unit

        setupTrans.transact(transactor).run

        val response: List[SharedCollection] =
          collectionPersistenceServices
            .getTopCollectionsByCategory(communicationCategory, offset, limit)
            .transact(transactor).run

        response must beEmpty
      }
    }

    "return a list of top collections for the given category" in {
      prop { (userData: UserData, collectionsData: List[SharedCollectionData]) ⇒

        val List(socialCollections, otherCollections) = divideList(2, collectionsData)

        val setupTrans = for {
          userId ← createUser(userData)
          socialCollections ← createCollectionsWithCategoryAndUser(
            collectionsData = socialCollections,
            category        = socialCategory,
            userId          = Option(userId)
          )
          otherCollections ← createCollectionsWithCategoryAndUser(
            collectionsData = otherCollections,
            category        = communicationCategory,
            userId          = Option(userId)
          )
        } yield Unit

        setupTrans.transact(transactor).run

        val response = for {
          response ← collectionPersistenceServices.getTopCollectionsByCategory(
            category = socialCategory,
            offset   = offset,
            limit    = limit
          )
          _ ← deleteSharedCollections
        } yield response

        val collections: List[SharedCollection] = response.transact(transactor).run

        val sortedSocialCollections = socialCollections.sortWith(_.installations > _.installations)

        collections.size must be_<=(limit)
        collections.headOption.map(_.publicIdentifier) must_== sortedSocialCollections.headOption.map(_.publicIdentifier)
      }
    }
  }

  "addPackage" should {
    "create a new package associated with an existing shared collection" in {
      prop { (userData: UserData, collectionData: SharedCollectionData, packageName: String) ⇒
        val collectionId = (for {
          u ← createUser(userData)
          c ← createCollectionWithUser(collectionData, Option(u))
        } yield c).transact(transactor).run

        val packageId = collectionPersistenceServices.addPackage[Long](
          collectionId,
          packageName
        ).transact(transactor).run

        val storedPackages = collectionPersistenceServices.getPackagesByCollection(
          collectionId
        ).transact(transactor).run

        storedPackages must contain { p: SharedCollectionPackage ⇒
          p.id must_== packageId
        }.atMostOnce
      }
    }
  }

  "addPackages" should {
    "create new packages associated with an existing shared collection" in {
      prop { (userData: UserData, collectionData: SharedCollectionData, packagesName: List[String]) ⇒
        val collectionId = (for {
          u ← createUser(userData)
          c ← createCollectionWithUser(collectionData, Option(u))
        } yield c).transact(transactor).run

        val created = collectionPersistenceServices.addPackages(
          collectionId,
          packagesName
        ).transact(transactor).run

        created must_== packagesName.size
      }
    }
  }

  "getPackagesByCollection" should {
    "return an empty list if the table is empty" in {
      prop { (collectionId: Long) ⇒
        val packages = collectionPersistenceServices.getPackagesByCollection(
          collectionId
        ).transact(transactor).run

        packages must beEmpty
      }
    }
    "return a list of packages associated with the given shared collection" in {

      prop { (userData: UserData, collectionData: SharedCollectionData, packagesName: List[String]) ⇒
        val collectionId = (for {
          u ← createUser(userData)
          c ← createCollectionWithUser(collectionData, Option(u))
          _ ← createPackages(c, packagesName)
        } yield c).transact(transactor).run

        val packages = collectionPersistenceServices.getPackagesByCollection(
          collectionId
        ).transact(transactor).run

        packages must haveSize(packagesName.size)

        packages must contain { p: SharedCollectionPackage ⇒
          p.sharedCollectionId must_=== collectionId
        }.forall
      }
    }
    "return an empty list if there isn't any package associated with the given collection" in {
      prop { (userData: UserData, collectionData: SharedCollectionData, packagesName: List[String]) ⇒
        val collectionId = (for {
          u ← createUser(userData)
          c ← createCollectionWithUser(collectionData, Option(u))
          _ ← createPackages(c, packagesName)
        } yield c).transact(transactor).run

        val packages = collectionPersistenceServices.getPackagesByCollection(
          collectionId + 1000000
        ).transact(transactor).run

        packages must beEmpty
      }
    }
  }

  "updateCollection" should {
    "return 0 updated rows if the table is empty" in {
      prop { (id: Long, title: String, description: Option[String]) ⇒
        val updatedCollectionCount = collectionPersistenceServices.updateCollectionInfo(
          id          = id,
          title       = title,
          description = description
        ).transact(transactor).run

        updatedCollectionCount must_== 0
      }
    }
    "return 1 updated row if there is a collection with the given id in the database" in {
      prop { (userData: UserData, collectionData: SharedCollectionData, newTitle: String, newDescription: Option[String]) ⇒
        val id = (for {
          u ← insertItem(User.Queries.insert, userData.toTuple)
          c ← insertItem(SharedCollection.Queries.insert, collectionData.copy(userId = Option(u)).toTuple)
          _ ← collectionPersistenceServices.updateCollectionInfo(c, newTitle, newDescription)
        } yield c).transact(transactor).run

        val storedCollection = collectionPersistenceServices.getCollectionById(
          id = id
        ).transact(transactor).run

        storedCollection must beSome[SharedCollection].which {
          collection ⇒
            collection.name must_== newTitle
            collection.description must_== newDescription
        }
      }
    }
    "return 0 updated rows if there isn't any collection with the given id in the database" in {
      prop { (userData: UserData, collectionData: SharedCollectionData, newTitle: String, newDescription: Option[String]) ⇒
        val id = (for {
          u ← insertItem(User.Queries.insert, userData.toTuple)
          c ← insertItem(SharedCollection.Queries.insert, collectionData.copy(userId = Option(u)).toTuple)
        } yield c).transact(transactor).run

        val updatedCollectionCount = collectionPersistenceServices.updateCollectionInfo(
          id          = id + 1000000,
          title       = newTitle,
          description = newDescription
        ).transact(transactor).run

        updatedCollectionCount must_== 0
      }
    }
  }

}