package cards.nine.services.free.algebra

import cards.nine.domain.account.GoogleIdToken
import cards.nine.services.free.domain.{ TokenInfo, WrongTokenInfo }
import cats.data.Xor
import cats.free.Free
import io.freestyle.free

object GoogleApi {

  @free trait Services[F[_]] {

    def getTokenInfo(tokenId: GoogleIdToken): Free[F, WrongTokenInfo Xor TokenInfo]

  }

}
