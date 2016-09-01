package com.fortysevendeg.ninecards.googleplay.service.free.interpreter.googleapi

import cats.data.Xor
import cats.syntax.xor._
import com.fortysevendeg.ninecards.googleplay.domain.{Category, GoogleAuthParams, Localization, Package, PriceFilter}
import com.fortysevendeg.ninecards.googleplay.service.free.interpreter.googleapi.proto.GooglePlay.{ResponseWrapper, ListResponse, DocV2}
import org.http4s.Http4s._
import org.http4s.Status.ResponseClass.Successful
import org.http4s.{Header, Headers, Method, Query, Request, Response, Uri}
import org.http4s.client.Client
import scalaz.concurrent.Task
import scodec.bits.ByteVector

class ApiClient(config: Configuration, client: Client ) {

  object details {

    val uri: Uri = Uri(
      scheme = Option(config.protocol.ci),
      authority = Option(Uri.Authority(host = Uri.RegName(config.host) ))
    ).withPath(config.detailsPath)

    def apply( packageName: Package, authParams: GoogleAuthParams ) : Task[Xor[Response, DocV2]] = {
      def toDocV2(byteVector: ByteVector) : DocV2 =
        ResponseWrapper.parseFrom(byteVector.toArray).getPayload.getDetailsResponse.getDocV2

      val httpRequest: Request =
        new Request(
          method = Method.GET,
          uri = uri.withQueryParam( "doc", packageName.value),
          headers = Headers( authHeaders(authParams) ++ fixedHeaders)
        )
      run[Response,DocV2](httpRequest, (r => r), toDocV2)
    }

  }

  object list {

    val uri: Uri = Uri(
      scheme = Option(config.protocol.ci),
      authority = Option( Uri.Authority(host = Uri.RegName(config.host) ) )
    ).withPath(config.listPath)

    def apply( category: Category, priceFilter: PriceFilter, auth: GoogleAuthParams ): Task[Xor[Response, ListResponse]] = {

      val subCategory: String = priceFilter match {
        case PriceFilter.FREE => "apps_topselling_free"
        case PriceFilter.PAID => "apps_topselling_paid"
        case PriceFilter.ALL => "apps_togrossing"
      }

      val query: Query = Query.fromPairs(
        ( "c", "3" ),
        ( "cat", category.entryName ),
        ( "ctr",  subCategory )
      )

      val httpRequest: Request = new Request(
        method = Method.GET,
        uri = uri.copy( query = query),
        headers = Headers( authHeaders(auth) ++ fixedHeaders)
      )

      run[Response, ListResponse]( httpRequest, (r => r), toListResponse)
    }

    private[this] def toListResponse(bv: ByteVector) =
      ResponseWrapper.parseFrom(bv.toArray).getPayload.getListResponse
  }

  private[this] def run[L,R](request: Request, failed: Response => L, success: ByteVector => R ): Task[Xor[L,R]] = {
    client.fetch(request) {
      case Successful(resp) => resp.as[ByteVector].map( bv => success(bv).right[L] )
      case resp => Task.now(failed(resp).left[R])
    }
  }

  private[this] def authHeaders(auth: GoogleAuthParams): List[Header] = {
    Header("Authorization", s"GoogleLogin auth=${auth.token.value}") ::
    Header("X-DFE-Device-Id", auth.androidId.value) :: (
      auth.localization match {
        case Some(Localization(locale)) => List( Header("Accept-Language", locale) )
        case None => List()
      }
    )
  }

  /* Note of development: this set of headers were directly copied from the code of the
   * google-play-crawler, but it is not clear what functions they perform. */
  private[this] lazy val fixedHeaders: List[Header] = {
    val userAgentValue = {
      val ls = List(
        "api=3", "versionCode=8016014", "sdk=15",
        "device=GT-I9300", "hardware=aries", "product=GT-I9300"
      ).mkString(",")
      s"Android-Finsky/3.10.14 (${ls})"
    }
    val unsopportedExperimentsValue = List(
      "nocache:billing.use_charging_poller",
      "market_emails",
      "buyer_currency",
      "prod_baseline",
      "checkin.set_asset_paid_app_field",
      "shekel_test",
      "content_ratings",
      "buyer_currency_in_app",
      "nocache:encrypted_apk",
      "recent_changes"
    ).mkString(",")

    List(
      Header("Accept-Language", "en-EN"),
      Header("Content-Type", "application/json; charset=UTF-8"),
      Header("Host", "android.clients.google.com"),
      Header("User-Agent", userAgentValue),
      Header("X-DFE-Unsupported-Experiments", unsopportedExperimentsValue),
      Header("X-DFE-Client-Id", "am-android-google"),
      Header("X-DFE-Enabled-Experiments", "cl:billing.select_add_instrument_by_default"),
      Header("X-DFE-Filter-Level", "3"),
      Header("X-DFE-SmallestScreenWidthDp", "320")
    )
  }

}