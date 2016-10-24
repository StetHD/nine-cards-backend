package cards.nine.services.free.interpreter.collection

import cards.nine.commons.NineCardsErrors.NineCardsError
import cards.nine.domain.pagination.Page
import cards.nine.services.free.algebra
import cards.nine.services.free.algebra.SharedCollection._
import cards.nine.services.free.domain.{ SharedCollection, SharedCollectionWithAggregatedInfo, User }
import cards.nine.services.free.interpreter.collection.Services.SharedCollectionData
import cards.nine.services.free.interpreter.user.Services.UserData
import cards.nine.services.persistence.NineCardsGenEntities.{ CollectionTitle, PublicIdentifier }
import cards.nine.services.persistence.{ DomainDatabaseContext, NineCardsScalacheckGen }
import doobie.contrib.postgresql.pgtypes._
import doobie.imports.ConnectionIO
import org.specs2.ScalaCheck
import org.specs2.matcher.{ DisjunctionMatchers, MatchResult }
import org.specs2.mutable.Specification
import shapeless.syntax.std.product._

import scala.annotation.tailrec
import scala.collection.immutable.List
import scalaz.syntax.traverse.ToTraverseOps
import scalaz.std.list._

trait SharedCollectionPersistenceServicesContext extends DomainDatabaseContext {

  val communicationCategory = "COMMUNICATION"

  val pageNumber = 0

  val pageSize = 25

  val pageParams = Page(pageNumber, pageSize)

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

  def createUser(userData: UserData): ConnectionIO[Long] = insertItem(User.Queries.insert, userData)

  def deleteSharedCollections: ConnectionIO[Int] = deleteItems(deleteSharedCollectionsQuery)

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

  object WithData {

    def apply[A](userData: UserData)(check: Long ⇒ MatchResult[A]) = {
      val user = insertItem(User.Queries.insert, userData.toTuple).transactAndRun

      check(user)
    }

    def apply[A](userData: UserData, collectionData: SharedCollectionData)(check: Long ⇒ MatchResult[A]) = {
      val collection = {
        for {
          user ← insertItem(User.Queries.insert, userData.toTuple)
          collection ← insertItem(SharedCollection.Queries.insert, collectionData.copy(userId = Option(user)).toTuple)
        } yield collection
      }.transactAndRun

      check(collection)
    }

    def apply[A](
      ownerData: UserData,
      otherUserData: UserData,
      ownedCollectionData: List[SharedCollectionData],
      foreignCollectionData: List[SharedCollectionData],
      disownedCollectionData: List[SharedCollectionData]
    )(check: (Long, List[Long]) ⇒ MatchResult[A]) = {
      val (owner, ownedCollections) = {
        for {
          ownerId ← createUser(ownerData)
          otherId ← createUser(otherUserData)
          owned ← ownedCollectionData traverse (createCollectionWithUser(_, Option(ownerId)))
          foreign ← foreignCollectionData traverse (createCollectionWithUser(_, Option(otherId)))
          disowned ← disownedCollectionData traverse (createCollectionWithUser(_, None))
        } yield (ownerId, owned)
      }.transactAndRun

      check(owner, ownedCollections)
    }

    def apply[A](
      userData: UserData,
      collectionsData: List[SharedCollectionData],
      category: String
    )(check: ⇒ MatchResult[A]) = {
      {
        for {
          userId ← createUser(userData)
          collections ← createCollectionsWithCategoryAndUser(
            collectionsData = collectionsData,
            category        = category,
            userId          = Option(userId)
          )
        } yield Unit
      }.transactAndRun

      check
    }

    def apply[A](
      userData: UserData,
      collectionsData: List[SharedCollectionData],
      category: String,
      otherCollectionsData: List[SharedCollectionData],
      otherCategory: String
    )(check: ⇒ MatchResult[A]) = {
      {
        for {
          _ ← deleteSharedCollections
          userId ← createUser(userData)
          collections ← createCollectionsWithCategoryAndUser(
            collectionsData = collectionsData,
            category        = category,
            userId          = Option(userId)
          )
          otherCollections ← createCollectionsWithCategoryAndUser(
            collectionsData = otherCollectionsData,
            category        = otherCategory,
            userId          = Option(userId)
          )
        } yield Unit
      }.transactAndRun

      check
    }
  }

  def runService[A](op: algebra.SharedCollection.Ops[A]) = collectionPersistenceServices.apply(op)
}

class ServicesSpec
  extends Specification
  with ScalaCheck
  with DisjunctionMatchers
  with NineCardsScalacheckGen
  with SharedCollectionPersistenceServicesContext {

  sequential

  "addCollection" should {
    "create a new shared collection when an existing user id is given" in {
      prop { (userData: UserData, collectionData: SharedCollectionData) ⇒

        WithData(userData) { user ⇒
          val insertedCollection = runService(
            Add(collectionData.copy(userId = Option(user)))
          ).transactAndRun

          insertedCollection must beRight[SharedCollection].which { collection ⇒
            collection.publicIdentifier must_== collectionData.publicIdentifier
            collection.userId must beSome(user)
          }
        }
      }
    }
    "create a new shared collection without a defined user id" in {
      prop { (collectionData: SharedCollectionData) ⇒
        WithEmptyDatabase {
          val insertedCollection = collectionPersistenceServices.add(
            collectionData.copy(userId = None)
          ).transactAndRun

          insertedCollection must beRight[SharedCollection].which { collection ⇒
            collection.publicIdentifier must_== collectionData.publicIdentifier
            collection.userId must beNone
          }
        }
      }
    }
  }

  "getById" should {
    "return a SharedCollectionNotFound error if the table is empty" in {
      prop { (id: Long) ⇒
        WithEmptyDatabase {
          val collection = runService(GetById(id)).transactAndRun

          collection must beLeft[NineCardsError]
        }
      }
    }
    "return a collection if there is a record with the given id in the database" in {
      prop { (userData: UserData, collectionData: SharedCollectionData) ⇒

        WithData(userData, collectionData) { collectionId ⇒
          val collection = runService(
            GetById(collectionId)
          ).transactAndRun

          collection must beRight[SharedCollection].which {
            c ⇒ c.publicIdentifier must_== collectionData.publicIdentifier
          }
        }
      }
    }
    "return a SharedCollectionNotFound error if there isn't any collection with the given id in the database" in {
      prop { (userData: UserData, collectionData: SharedCollectionData) ⇒

        WithData(userData, collectionData) { collectionId ⇒

          val collection = runService(
            GetById(collectionId * -1)
          ).transactAndRun

          collection must beLeft[NineCardsError]
        }
      }
    }
  }

  "getByPublicIdentifier" should {
    "return a SharedCollectionNotFound error if the table is empty" in {
      prop { publicIdentifier: PublicIdentifier ⇒
        WithEmptyDatabase {
          val collection = runService(
            GetByPublicId(publicIdentifier.value)
          ).transactAndRun

          collection must beLeft[NineCardsError]
        }
      }
    }
    "return a collection if there is a record with the given public identifier in the database" in {
      prop { (userData: UserData, collectionData: SharedCollectionData) ⇒
        WithData(userData, collectionData) { collectionId ⇒

          val collection = runService(
            GetByPublicId(collectionData.publicIdentifier)
          ).transactAndRun

          collection must beRight[SharedCollection].which {
            collection ⇒
              collection.id must_== collectionId
              collection.publicIdentifier must_== collectionData.publicIdentifier
          }
        }
      }
    }
    "return a SharedCollectionNotFound error if there isn't any collection with the given " +
      "public identifier in the database" in {
        prop { (userData: UserData, collectionData: SharedCollectionData) ⇒

          WithData(userData, collectionData) { _ ⇒

            val collection = runService(
              GetByPublicId(collectionData.publicIdentifier.reverse)
            ).transactAndRun

            collection must beLeft[NineCardsError]
          }
        }
      }
  }

  "getByUserId" should {

    "return the list of Collections created by the User" in {
      prop { (ownerData: UserData, otherData: UserData, collectionData: List[SharedCollectionData]) ⇒
        val List(ownedData, disownedData, foreignData) = divideList[SharedCollectionData](3, collectionData)

        WithData(ownerData, otherData, ownedData, disownedData, foreignData) { (owner, ownedCollections) ⇒

          val response = runService(
            GetByUser(owner)
          ).transactAndRun

          response must beRight[List[SharedCollectionWithAggregatedInfo]].which { list ⇒

            (list map (_.sharedCollectionData.id)) must containTheSameElementsAs(ownedCollections)
          }
        }
      }
    }
  }

  "getLatestByCategory" should {
    "return an empty list of collections if the table is empty" in {
      prop { category: String ⇒
        WithEmptyDatabase {
          val response = runService(
            GetLatestByCategory(
              category   = category,
              pageParams = pageParams
            )
          ).transactAndRun

          response must beRight[List[SharedCollection]](Nil)
        }
      }
    }
    "return an empty list of collections if there are no records with the given category" in {
      prop { (userData: UserData, collectionsData: List[SharedCollectionData]) ⇒

        WithData(userData, collectionsData, socialCategory) {
          val response = runService(
            GetLatestByCategory(
              category   = communicationCategory,
              pageParams = pageParams
            )
          ).transactAndRun

          response must beRight[List[SharedCollection]](Nil)
        }
      }
    }
    "return a list of latest collections for the given category" in {
      prop { (userData: UserData, collectionsData: List[SharedCollectionData]) ⇒

        val List(socialCollections, otherCollections) = divideList(2, collectionsData)

        WithData(userData, socialCollections, socialCategory, otherCollections, communicationCategory) {

          val collections = runService(
            GetLatestByCategory(
              category   = socialCategory,
              pageParams = pageParams
            )
          ).transactAndRun

          val sortedSocialCollections = socialCollections.sortWith(_.publishedOn.getTime > _.publishedOn.getTime)

          collections must beRight[List[SharedCollection]].which { list ⇒
            list.size must be_<=(pageSize)
            list.headOption.map(_.publicIdentifier) must_== sortedSocialCollections.headOption.map(_.publicIdentifier)
          }
        }
      }
    }
  }

  "getTopByCategory" should {

    "return an empty list of collections if the table is empty" in {
      prop { i: Int ⇒
        WithEmptyDatabase {
          val response = runService(
            GetTopByCategory(
              category   = socialCategory,
              pageParams = pageParams
            )
          ).transactAndRun

          response must beRight[List[SharedCollection]](Nil)
        }
      }
    }
    "return an empty list of collections if there are no records with the given category" in {
      prop { (userData: UserData, collectionsData: List[SharedCollectionData]) ⇒

        WithData(userData, collectionsData, socialCategory) {
          val response = runService(
            GetTopByCategory(
              category   = communicationCategory,
              pageParams = pageParams
            )
          ).transactAndRun

          response must beRight[List[SharedCollection]](Nil)
        }
      }
    }
    "return a list of top collections for the given category" in {
      prop { (userData: UserData, collectionsData: List[SharedCollectionData]) ⇒

        val List(socialCollections, otherCollections) = divideList(2, collectionsData)

        WithData(userData, socialCollections, socialCategory, otherCollections, communicationCategory) {

          val collections = runService(
            GetTopByCategory(
              category   = socialCategory,
              pageParams = pageParams
            )
          ).transactAndRun

          val maxViews =
            if (socialCollections.isEmpty)
              None
            else
              Option(socialCollections.map(_.views).max)

          collections must beRight[List[SharedCollection]].which { list ⇒
            list.size must be_<=(pageSize)
            list.headOption.map(_.views) must_== maxViews
          }
        }
      }
    }
  }

  "updateCollectionInfo" should {
    "return 0 updated rows if the table is empty" in {
      prop { (id: Long, title: CollectionTitle) ⇒
        WithEmptyDatabase {
          val updatedCollectionCount = runService(
            Update(
              id    = id,
              title = title.value
            )
          ).transactAndRun

          updatedCollectionCount must beRight[Int](0)
        }
      }
    }
    "return 1 updated row if there is a collection with the given id in the database" in {
      prop { (userData: UserData, collectionData: SharedCollectionData, newTitle: CollectionTitle) ⇒

        WithData(userData, collectionData) { collectionId ⇒

          runService(
            Update(
              id    = collectionId,
              title = newTitle.value
            )
          ).transactAndRun

          val collection = getItem[Long, SharedCollection](
            sql    = SharedCollection.Queries.getById,
            values = collectionId
          ).transactAndRun

          collection.name must_== newTitle.value
        }
      }
    }
    "return 0 updated rows if there isn't any collection with the given id in the database" in {
      prop { (userData: UserData, collectionData: SharedCollectionData, newTitle: CollectionTitle) ⇒

        WithData(userData, collectionData) { collectionId ⇒

          val updatedCollectionCount = runService(
            Update(
              id    = collectionId * -1,
              title = newTitle.value
            )
          ).transactAndRun

          updatedCollectionCount must beRight[Int](0)
        }
      }
    }
  }
}
