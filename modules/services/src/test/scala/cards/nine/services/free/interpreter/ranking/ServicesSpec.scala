package cards.nine.services.free.interpreter.ranking

import cards.nine.commons.NineCardsErrors.NineCardsError
import cards.nine.domain.ScalaCheck._
import cards.nine.domain.analytics.{ GeoScope, RankedApp, UnrankedApp, UpdateRankingSummary }
import cards.nine.domain.application.Moment
import cards.nine.services.free.algebra.Ranking
import cards.nine.services.free.algebra.Ranking.{ GetRanking, GetRankingForApps, UpdateRanking }
import cards.nine.services.free.domain.Ranking.{ CacheKey, CacheVal, GoogleAnalyticsRanking }
import cards.nine.services.free.interpreter.ranking.Services._
import cards.nine.services.persistence.NineCardsScalacheckGen
import com.redis.RedisClient
import io.circe.generic.auto._
import org.specs2.ScalaCheck
import org.specs2.matcher.{ MatchResult, Matcher }
import org.specs2.mutable.Specification
import org.specs2.specification.{ BeforeAfterAll, BeforeEach }
import redis.embedded.RedisServer

trait RedisContext {

  val services = Services.services
  lazy val redisServer: RedisServer = new RedisServer()
  lazy val redisClient: RedisClient = new RedisClient(host = "localhost", port = redisServer.getPort)

  def findEntry(key: CacheKey): Option[CacheVal] =
    redisClient.get[Option[CacheVal]](key)(keyAndValFormat, valParse).flatten

  def runService[A](op: Ranking.Ops[A]) = services.apply(op)(redisClient).unsafePerformSync

}

class ServicesSpec
  extends Specification
  with BeforeAfterAll
  with BeforeEach
  with NineCardsScalacheckGen
  with RedisContext
  with ScalaCheck {

  override def afterAll(): Unit = redisServer.stop()

  override def beforeAll(): Unit = redisServer.start()

  override protected def before: Any = redisClient.flushall

  object WithCachedData {

    def apply[B](scope: GeoScope, ranking: GoogleAnalyticsRanking)(check: ⇒ MatchResult[B]) = {
      redisClient.flushall
      redisClient.set(CacheKey.fromScope(scope), CacheVal(Option(ranking)))
      check
    }
  }

  def rankedAppWithinMomentMatcher(isMoment: Boolean): Matcher[RankedApp] = { app: RankedApp ⇒
    Moment.isMoment(app.category) must_== isMoment
  }

  sequential

  "GetRanking" should {
    "return an empty ranking if there is no info for the given scope" in {
      prop { scope: GeoScope ⇒
        runService(GetRanking(scope)) must beLeft[NineCardsError]
      }
    }

    "return a ranking value if there is info for the given scope" in {
      prop { (scope: GeoScope, ranking: GoogleAnalyticsRanking) ⇒
        WithCachedData(scope, ranking) {
          runService(GetRanking(scope)) must beRight[GoogleAnalyticsRanking].which {
            rankingFromCache ⇒
              rankingFromCache.categories must not be empty
              rankingFromCache.categories.toList must containTheSameElementsAs(ranking.categories.toList)
          }
        }
      }
    }
  }

  "GetRankingForApps" should {
    "return an empty list of ranked apps if there is no ranking in cache for the given scope" in {
      prop { (scope: GeoScope, unrankedApps: Set[UnrankedApp]) ⇒
        runService(GetRankingForApps(scope, unrankedApps)) must beRight[List[RankedApp]].which {
          apps ⇒
            apps must beEmpty
        }
      }
    }
    "return a list of ranked apps for those apps whith ranking info for the given scope" in {
      prop { (scope: GeoScope, sample: GetRankingForAppsSample) ⇒
        WithCachedData(scope, sample.ranking) {
          runService(GetRankingForApps(scope, sample.unrankedApps)) must beRight[List[RankedApp]].which {
            apps ⇒
              apps.map(_.packageName) must containTheSameElementsAs(sample.appsWithRanking)
              apps.map(_.packageName) must not contain anyOf(sample.appsWithoutRanking: _*)
              apps must contain(rankedAppWithinMomentMatcher(isMoment = false)).forall
          }
        }
      }
    }
  }

  "UpdateRanking" should {
    "insert a new entry into the cache if no entry was created for the given key previously" in {
      prop { (scope: GeoScope, ranking: GoogleAnalyticsRanking) ⇒
        runService(UpdateRanking(scope, ranking)) must beRight[UpdateRankingSummary].which {
          summary ⇒
            summary.created must_== ranking.categories.values.size
        }
      }
    }

    "overwrite the existing entry for the given key with the new ranking" in {
      prop { (scope: GeoScope, ranking: GoogleAnalyticsRanking, newRanking: GoogleAnalyticsRanking) ⇒
        WithCachedData(scope, ranking) {
          runService(UpdateRanking(scope, newRanking)) must beRight[UpdateRankingSummary].which {
            summary ⇒
              summary.created must_== newRanking.categories.values.size
          }

          findEntry(CacheKey.fromScope(scope)) must beSome[CacheVal].which { value ⇒
            value.ranking must beSome[GoogleAnalyticsRanking].which { rankingFromCache ⇒
              rankingFromCache.categories must not be empty
              rankingFromCache.categories.toList must containTheSameElementsAs(newRanking.categories.toList)
            }
          }
        }
      }
    }
  }
}
