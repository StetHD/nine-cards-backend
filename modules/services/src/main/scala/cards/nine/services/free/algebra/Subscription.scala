package cards.nine.services.free.algebra

import cards.nine.services.free.domain.SharedCollectionSubscription
import cats.free.Free
import io.freestyle.free

object Subscription {

  @free trait Services[F[_]] {

    def add(collection: Long, user: Long, collectionPublicId: String): Free[F, Int]

    def getByCollectionAndUser(collection: Long, user: Long): Free[F, Option[SharedCollectionSubscription]]

    def getByCollection(collection: Long): Free[F, List[SharedCollectionSubscription]]

    def getByUser(user: Long): Free[F, List[SharedCollectionSubscription]]

    def removeByCollectionAndUser(collection: Long, user: Long): Free[F, Int]
  }

}
