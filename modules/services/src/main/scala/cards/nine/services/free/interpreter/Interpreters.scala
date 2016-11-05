package cards.nine.services.free.interpreter

import cards.nine.commons.NineCardsConfig._
import cards.nine.commons.TaskInstances
import cards.nine.googleplay.processes.withTypes.WithRedisClient
import cards.nine.services.free.algebra._
import cards.nine.services.free.interpreter.analytics.{ Services ⇒ AnalyticsServices }
import cards.nine.services.free.interpreter.collection.{ Services ⇒ CollectionServices }
import cards.nine.services.free.interpreter.country.{ Services ⇒ CountryServices }
import cards.nine.services.free.interpreter.firebase.{ Services ⇒ FirebaseServices }
import cards.nine.services.free.interpreter.googleapi.{ Services ⇒ GoogleApiServices }
import cards.nine.services.free.interpreter.googleplay.{ Services ⇒ GooglePlayServices }
import cards.nine.services.free.interpreter.ranking.Services._
import cards.nine.services.free.interpreter.ranking.{ Services ⇒ RankingServices }
import cards.nine.services.free.interpreter.subscription.{ Services ⇒ SubscriptionServices }
import cards.nine.services.free.interpreter.user.{ Services ⇒ UserServices }
import cards.nine.services.persistence.DatabaseTransactor
import cats._
import com.redis.RedisClientPool
import doobie.imports._

import scalaz.concurrent.Task

trait Interpreters extends TaskInstances {

  val connectionIO2Task = new (ConnectionIO ~> Task) {
    def apply[A](fa: ConnectionIO[A]): Task[A] = fa.transact(DatabaseTransactor.transactor)
  }

  val redis2Task = new (WithRedisClient ~> Task) {
    def apply[A](fa: WithRedisClient[A]): Task[A] = redisClientPool.withClient(fa)
  }

  val redisClientPool: RedisClientPool = {
    val baseConfig = "ninecards.google.play.redis"
    new RedisClientPool(
      host   = defaultConfig.getString(s"$baseConfig.host"),
      port   = defaultConfig.getInt(s"$baseConfig.port"),
      secret = defaultConfig.getOptionalString(s"$baseConfig.secret")
    )
  }

  implicit val analyticsInterpreter: (GoogleAnalytics.Services.T ~> Task) =
    AnalyticsServices.services

  implicit val collectionInterpreter: (SharedCollection.Services.T ~> Task) =
    CollectionServices.services.andThen(connectionIO2Task)

  implicit val countryInterpreter: (Country.Services.T ~> Task) =
    CountryServices.services.andThen(connectionIO2Task)

  implicit val firebaseInterpreter: (Firebase.Services.T ~> Task) = FirebaseServices.services

  implicit val googleApiInterpreter: (GoogleApi.Services.T ~> Task) = GoogleApiServices.services

  implicit val googlePlayInterpreter: (GooglePlay.Services.T ~> Task) = GooglePlayServices.services

  implicit val rankingInterpreter: (Ranking.Services.T ~> Task) =
    RankingServices.services.andThen(redis2Task)

  implicit val subscriptionInterpreter: (Subscription.Services.T ~> Task) =
    SubscriptionServices.services.andThen(connectionIO2Task)

  implicit val userInterpreter: (User.Services.T ~> Task) =
    UserServices.services.andThen(connectionIO2Task)
}

object Interpreters extends Interpreters