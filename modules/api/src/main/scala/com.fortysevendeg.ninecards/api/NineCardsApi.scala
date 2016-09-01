package com.fortysevendeg.ninecards.api

import akka.actor.{ Actor, ActorRefFactory }
import cats.data.Xor
import cats.free.Free
import com.fortysevendeg.ninecards.api.NineCardsDirectives._
import com.fortysevendeg.ninecards.api.NineCardsHeaders.Domain._
import com.fortysevendeg.ninecards.api.converters.Converters._
import com.fortysevendeg.ninecards.api.messages.GooglePlayMessages._
import com.fortysevendeg.ninecards.api.messages.InstallationsMessages._
import com.fortysevendeg.ninecards.api.messages.SharedCollectionMessages._
import com.fortysevendeg.ninecards.api.messages.UserMessages._
import com.fortysevendeg.ninecards.api.utils.SprayMarshallers._
import com.fortysevendeg.ninecards.api.utils.SprayMatchers._
import com.fortysevendeg.ninecards.processes.NineCardsServices._
import com.fortysevendeg.ninecards.processes._
import com.fortysevendeg.ninecards.services.free.domain.Category
import com.fortysevendeg.ninecards.services.free.domain.rankings._
import spray.http.StatusCodes.NotFound
import spray.routing._

import scala.concurrent.ExecutionContext

class NineCardsApiActor
  extends Actor
  with AuthHeadersRejectionHandler
  with HttpService
  with NineCardsExceptionHandler {

  override val actorRefFactory = context

  implicit val executionContext: ExecutionContext = actorRefFactory.dispatcher

  import com.fortysevendeg.ninecards.services.persistence.CustomComposite.rankingEntry

  def receive = runRoute(new NineCardsRoutes().nineCardsRoutes)

}

class NineCardsRoutes(
  implicit
  userProcesses: UserProcesses[NineCardsServices],
  googleApiProcesses: GoogleApiProcesses[NineCardsServices],
  applicationProcesses: ApplicationProcesses[NineCardsServices],
  rankingProcesses: RankingProcesses[NineCardsServices],
  sharedCollectionProcesses: SharedCollectionProcesses[NineCardsServices],
  refFactory: ActorRefFactory,
  executionContext: ExecutionContext
) {

  import Directives._
  import JsonFormats._

  val nineCardsRoutes: Route = pathPrefix(Segment) {
    case "apiDocs" ⇒ swaggerRoute
    case "collections" ⇒ sharedCollectionsRoute
    case "applications" ⇒ applicationRoute
    case "installations" ⇒ installationsRoute
    case "login" ⇒ userRoute
    case "rankings" ⇒ rankings.route
    case _ ⇒ complete(NotFound)
  }

  private[this] lazy val userRoute: Route =
    pathEndOrSingleSlash {
      post {
        entity(as[ApiLoginRequest]) { request ⇒
          nineCardsDirectives.authenticateLoginRequest { sessionToken: SessionToken ⇒
            complete {
              userProcesses.signUpUser(toLoginRequest(request, sessionToken)) map toApiLoginResponse
            }
          }
        }
      }
    }

  private[this] lazy val applicationRoute: Route =
    nineCardsDirectives.authenticateUser { userContext ⇒
      path("categorize") {
        post {
          nineCardsDirectives.googlePlayInfo { googlePlayContext ⇒
            entity(as[ApiCategorizeAppsRequest]) { request ⇒
              complete(categorizeApps(request, googlePlayContext, userContext))
            }
          }
        }
      }
    }

  private[this] lazy val installationsRoute: Route =
    nineCardsDirectives.authenticateUser { implicit userContext: UserContext ⇒
      pathEndOrSingleSlash {
        put {
          entity(as[ApiUpdateInstallationRequest]) { request ⇒
            complete(updateInstallation(request, userContext))
          }
        }
      }
    }

  private[this] lazy val sharedCollectionsRoute: Route =
    nineCardsDirectives.authenticateUser { userContext: UserContext ⇒
      pathEndOrSingleSlash {
        post {
          entity(as[ApiCreateCollectionRequest]) { request ⇒
            nineCardsDirectives.generateNewCollectionInfo { collectionInfo: NewSharedCollectionInfo ⇒
              complete(createCollection(request, collectionInfo, userContext))
            }
          }
        } ~
          get {
            nineCardsDirectives.googlePlayInfo { googlePlayContext ⇒
              complete(getPublishedCollections(googlePlayContext, userContext))
            }
          }
      } ~
        (path("latest" / CategorySegment / TypedIntSegment[PageNumber] / TypedIntSegment[PageSize]) & get) {
          (category: Category, pageNumber: PageNumber, pageSize: PageSize) ⇒
            nineCardsDirectives.googlePlayInfo { googlePlayContext ⇒
              complete {
                getLatestCollectionsByCategory(
                  category          = category,
                  googlePlayContext = googlePlayContext,
                  userContext       = userContext,
                  pageNumber        = pageNumber,
                  pageSize          = pageSize
                )
              }
            }
        } ~
        (path("top" / CategorySegment / TypedIntSegment[PageNumber] / TypedIntSegment[PageSize]) & get) {
          (category: Category, pageNumber: PageNumber, pageSize: PageSize) ⇒
            nineCardsDirectives.googlePlayInfo { googlePlayContext ⇒
              complete {
                getTopCollectionsByCategory(
                  category          = category,
                  googlePlayContext = googlePlayContext,
                  userContext       = userContext,
                  pageNumber        = pageNumber,
                  pageSize          = pageSize
                )
              }
            }
        } ~
        path("subscriptions") {
          get {
            complete(getSubscriptionsByUser(userContext))
          }
        } ~
        pathPrefix(TypedSegment[PublicIdentifier]) { publicIdentifier ⇒
          pathEndOrSingleSlash {
            get {
              nineCardsDirectives.googlePlayInfo { googlePlayContext ⇒
                complete(getCollection(publicIdentifier, googlePlayContext, userContext))
              }
            } ~
              put {
                entity(as[ApiUpdateCollectionRequest]) { request ⇒
                  complete(updateCollection(publicIdentifier, request))
                }
              }
          } ~
            path("subscribe") {
              put(complete(subscribe(publicIdentifier, userContext))) ~
                delete(complete(unsubscribe(publicIdentifier, userContext)))
            }
        }
    }

  private[this] lazy val swaggerRoute: Route =
    // This path prefix grants access to the Swagger documentation.
    // Both /apiDocs/ and /apiDocs/index.html are valid paths to load Swagger-UI.
    pathEndOrSingleSlash {
      getFromResource("apiDocs/index.html")
    } ~ {
      getFromResourceDirectory("apiDocs")
    }

  private type NineCardsServed[A] = cats.free.Free[NineCardsServices, A]

  private[this] def updateInstallation(
    request: ApiUpdateInstallationRequest,
    userContext: UserContext
  ): NineCardsServed[ApiUpdateInstallationResponse] =
    userProcesses
      .updateInstallation(toUpdateInstallationRequest(request, userContext))
      .map(toApiUpdateInstallationResponse)

  private[this] def getCollection(
    publicId: PublicIdentifier,
    googlePlayContext: GooglePlayContext,
    userContext: UserContext
  ): NineCardsServed[XorApiGetCollectionByPublicId] =
    sharedCollectionProcesses
      .getCollectionByPublicIdentifier(publicId.value, toAuthParams(googlePlayContext, userContext))
      .map(_.map(r ⇒ toApiSharedCollection(r.data)))

  private[this] def createCollection(
    request: ApiCreateCollectionRequest,
    collectionInfo: NewSharedCollectionInfo,
    userContext: UserContext
  ): NineCardsServed[ApiCreateOrUpdateCollectionResponse] =
    sharedCollectionProcesses
      .createCollection(toCreateCollectionRequest(request, collectionInfo, userContext))
      .map(toApiCreateOrUpdateCollectionResponse)

  private[this] def subscribe(
    publicId: PublicIdentifier,
    userContext: UserContext
  ): NineCardsServed[Xor[Throwable, ApiSubscribeResponse]] =
    sharedCollectionProcesses
      .subscribe(publicId.value, userContext.userId.value)
      .map(_.map(toApiSubscribeResponse))

  private[this] def updateCollection(
    publicId: PublicIdentifier,
    request: ApiUpdateCollectionRequest
  ): NineCardsServed[Xor[Throwable, ApiCreateOrUpdateCollectionResponse]] =
    sharedCollectionProcesses
      .updateCollection(publicId.value, request.collectionInfo, request.packages)
      .map(_.map(toApiCreateOrUpdateCollectionResponse))

  private[this] def unsubscribe(
    publicId: PublicIdentifier,
    userContext: UserContext
  ): NineCardsServed[Xor[Throwable, ApiUnsubscribeResponse]] =
    sharedCollectionProcesses
      .unsubscribe(publicId.value, userContext.userId.value)
      .map(_.map(toApiUnsubscribeResponse))

  private[this] def getLatestCollectionsByCategory(
    category: Category,
    googlePlayContext: GooglePlayContext,
    userContext: UserContext,
    pageNumber: PageNumber,
    pageSize: PageSize
  ): NineCardsServed[ApiSharedCollectionList] =
    sharedCollectionProcesses
      .getLatestCollectionsByCategory(
        category   = category.entryName,
        authParams = toAuthParams(googlePlayContext, userContext),
        pageNumber = pageNumber.value,
        pageSize   = pageSize.value
      )
      .map(toApiSharedCollectionList)

  private[this] def getPublishedCollections(
    googlePlayContext: GooglePlayContext,
    userContext: UserContext
  ): NineCardsServed[ApiSharedCollectionList] =
    sharedCollectionProcesses
      .getPublishedCollections(userContext.userId.value, toAuthParams(googlePlayContext, userContext))
      .map(toApiSharedCollectionList)

  private[this] def getSubscriptionsByUser(
    userContext: UserContext
  ): NineCardsServed[ApiGetSubscriptionsByUser] =
    sharedCollectionProcesses
      .getSubscriptionsByUser(userContext.userId.value)
      .map(toApiGetSubscriptionsByUser)

  private[this] def getTopCollectionsByCategory(
    category: Category,
    googlePlayContext: GooglePlayContext,
    userContext: UserContext,
    pageNumber: PageNumber,
    pageSize: PageSize
  ): NineCardsServed[ApiSharedCollectionList] =
    sharedCollectionProcesses
      .getTopCollectionsByCategory(
        category   = category.entryName,
        authParams = toAuthParams(googlePlayContext, userContext),
        pageNumber = pageNumber.value,
        pageSize   = pageSize.value
      )
      .map(toApiSharedCollectionList)

  private[this] def categorizeApps(
    request: ApiCategorizeAppsRequest,
    googlePlayContext: GooglePlayContext,
    userContext: UserContext
  ): NineCardsServed[ApiCategorizeAppsResponse] =
    applicationProcesses
      .categorizeApps(request.items, toAuthParams(googlePlayContext, userContext))
      .map(toApiCategorizeAppsResponse)

  private[this] object rankings {

    import com.fortysevendeg.ninecards.api.converters.{ rankings ⇒ Converters }
    import com.fortysevendeg.ninecards.api.messages.{ rankings ⇒ Api }
    import io.circe.spray.JsonSupport._
    import NineCardsMarshallers._
    import Decoders.reloadRankingRequest

    lazy val route: Route =
      geographicScope { scope ⇒
        get {
          complete(getRanking(scope))
        } ~
          post {
            reloadParams(params ⇒ complete(reloadRanking(scope, params)))
          }
      }

    private[this] lazy val geographicScope: Directive1[GeoScope] = {
      val continent: Directive1[GeoScope] =
        path("continents" / ContinentSegment)
          .map(c ⇒ ContinentScope(c): GeoScope)
      val country: Directive1[GeoScope] =
        path("countries" / CountrySegment)
          .map(c ⇒ CountryScope(c): GeoScope)
      val world = path("world") & provide(WorldScope: GeoScope)

      world | continent | country
    }

    private[this] lazy val reloadParams: Directive1[RankingParams] =
      for {
        authToken ← headerValueByName(NineCardsHeaders.headerGoogleAnalyticsToken)
        apiRequest ← entity(as[Api.Reload.Request])
      } yield Converters.reload.toRankingParams(authToken, apiRequest)

    private[this] def reloadRanking(
      scope: GeoScope,
      params: RankingParams
    ): NineCardsServed[Api.Reload.XorResponse] =
      rankingProcesses.reloadRanking(scope, params).map(Converters.reload.toXorResponse)

    private[this] def getRanking(scope: GeoScope): NineCardsServed[Api.Ranking] =
      rankingProcesses.getRanking(scope).map(Converters.toApiRanking)

  }

}
