package cards.nine.processes

import cards.nine.services.free.algebra._
import cards.nine.services.free.interpreter.Interpreters
import io.freestyle.module

object App extends Interpreters {

  @module trait NineCardsApp[F[_]] {
    val analytics: GoogleAnalytics.Services[F]
    val collection: SharedCollection.Services[F]
    val country: Country.Services[F]
    val googleApi: GoogleApi.Services[F]
    val googlePlay: GooglePlay.Services[F]
    val notification: Firebase.Services[F]
    val ranking: Ranking.Services[F]
    val subscription: Subscription.Services[F]
    val user: User.Services[F]
  }

}