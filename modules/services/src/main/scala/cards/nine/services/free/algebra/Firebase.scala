package cards.nine.services.free.algebra

import cards.nine.services.free.domain.Firebase._
import cats.data.Xor
import cats.free.Free
import io.freestyle.free

object Firebase {

  @free trait Services[F[_]] {

    def sendUpdatedCollectionNotification(
      info: UpdatedCollectionNotificationInfo
    ): Free[F, FirebaseError Xor NotificationResponse]

  }

}
