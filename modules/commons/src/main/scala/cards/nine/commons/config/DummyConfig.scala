/*
 * Copyright 2017 47 Degrees, LLC. <http://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cards.nine.commons.config

import cards.nine.commons.config.Domain.NineCardsConfiguration

trait DummyConfig {

  object common {
    val protocol = "http"
    val port     = 8080
    val host     = "localhost"
  }

  object db {

    object default {
      val driver    = "org.postgresql.Driver"
      val urlPrefix = "jdbc:postgresql://"
      val url       = "postgres://ninecards_tester@localhost/ninecards_test"
    }

    object hikari {
      val maximumPoolSize = 1
      val maxLifetime     = 1
    }

  }

  object firebase {
    val authorizationKey = "1a2b3cb4d5e"

    object paths {
      val sendNotification = "/fcm/send"
    }

  }

  object googleanalytics {
    val uri    = "/v4/reports:batchGet"
    val viewId = "12345"
  }

  object googleapi {
    val tokenInfoUri         = "/oauth2/v3/tokeninfo"
    val tokenIdParameterName = "id_token"
  }

  object googleplay {

    object api {

      object paths {
        val bulkDetails     = "/fdfe/bulkDetails"
        val details         = "/fdfe/details"
        val list            = "/fdfe/list"
        val search          = "/fdfe/search"
        val recommendations = "/fdfe/rec"
      }

      val maxTotalConnections = 10
      val detailsBatchSize    = 5
    }

    object web {

      val maxTotalConnections = 3

      object paths {
        val details = "/store/apps/details"
      }

    }

    val resolveInterval  = "1 second"
    val resolveBatchSize = 1
  }

  object ninecards {
    val salt      = "0987654321"
    val secretKey = "1234567890"
  }

  object rankings {
    val actorInterval              = "1 hour"
    val rankingPeriod              = "30 days"
    val countriesPerRequest        = 2
    val maxNumberOfAppsPerCategory = 100

    object oauth {
      val clientId     = "12345"
      val clientEmail  = "client@ema.il"
      val privateKey   = """----BEGIN PRIVATE KEY----\nBASE64+=TEXT\n-----END PRIVATE KEY -----"""
      val privateKeyId = "abcdef0123456789"
      val tokenUri     = ""
      val scopes       = List("http://www.nine.cards/auth/testing.only")
    }
  }

  object redis {
    val url = "redis://localhost:6379"
  }

  val webmainpage: String = ""

  val editors: Map[String, String] = Map(
    "karl" → "microsoft",
    "marx" → "made",
    "gave" → "no"
  )

  val loaderIOToken = "loaderio-localtest"

  object test {
    val androidId               = "androidId"
    val token                   = "token"
    val localization            = "en-US"
    val googlePlayDetailsAppUrl = "https://play.google.com/store/apps/details"
  }

  def dummyConfigHocon(debugMode: Boolean) =
    s"""
       |ninecards {
       |  db {
       |    default {
       |      driver = "${db.default.driver}"
       |      urlPrefix = "${db.default.urlPrefix}"
       |      url = "${db.default.url}"
       |    }
       |    hikari {
       |      maximumPoolSize = ${db.hikari.maximumPoolSize}
       |      maxLifetime = ${db.hikari.maxLifetime}
       |    }
       |  }
       |  debugMode = $debugMode
       |  editors {
       |    karl = microsoft
       |    marx = made
       |    gave = no
       |  }
       |  google {
       |    analytics {
       |      host = "${common.host}"
       |      port = ${common.port}
       |      protocol = "${common.protocol}"
       |      path = "${googleanalytics.uri}"
       |      viewId = "${googleanalytics.viewId}"
       |    }
       |    api {
       |      host = "${common.host}"
       |      port = ${common.port}
       |      protocol = "${common.protocol}"
       |      tokenInfo {
       |        path = "${googleapi.tokenInfoUri}"
       |        tokenIdQueryParameter = "${googleapi.tokenIdParameterName}"
       |      }
       |    }
       |    firebase {
       |      authorizationKey = "${firebase.authorizationKey}"
       |      protocol = "${common.protocol}"
       |      host = "${common.host}"
       |      port = ${common.port}
       |      paths {
       |        sendNotification = "${firebase.paths.sendNotification}"
       |      }
       |    }
       |    play {
       |      api {
       |        protocol = "${common.protocol}"
       |        host = "${common.host}"
       |        port = ${common.port}
       |        maxTotalConnections = ${googleplay.api.maxTotalConnections}
       |        detailsBatchSize = ${googleplay.api.detailsBatchSize}
       |        paths {
       |          bulkDetails = "${googleplay.api.paths.bulkDetails}"
       |          details = "${googleplay.api.paths.details}"
       |          list = "${googleplay.api.paths.list}"
       |          search = "${googleplay.api.paths.search}"
       |          recommendations = "${googleplay.api.paths.recommendations}"
       |        }
       |      }
       |      web {
       |        maxTotalConnections = ${googleplay.web.maxTotalConnections}
       |        protocol = "${common.protocol}"
       |        host = "${common.host}"
       |        port = ${common.port}
       |        paths {
       |          details = "${googleplay.web.paths.details}"
       |        }
       |      }
       |      resolveInterval = ${googleplay.resolveInterval}
       |      resolveBatchSize = ${googleplay.resolveBatchSize}
       |    }
       |  }
       |    http {
       |    host = "${common.host}"
       |    port = ${common.port}
       |  }
       |  rankings {
       |    actorInterval = ${rankings.actorInterval}
       |    rankingPeriod = ${rankings.rankingPeriod}
       |    countriesPerRequest = ${rankings.countriesPerRequest}
       |    maxNumberOfAppsPerCategory = ${rankings.maxNumberOfAppsPerCategory}
       |    oauth {
       |      clientId = "${rankings.oauth.clientId}"
       |      clientEmail = "${rankings.oauth.clientEmail}"
       |      privateKey = "${rankings.oauth.privateKey}"
       |      privateKeyId = "${rankings.oauth.privateKeyId}"
       |      tokenUri = "${rankings.oauth.tokenUri}"
       |      scopes = [ "${rankings.oauth.scopes.head}" ]
       |    }
       |  }
       |  redis {
       |    url = "${redis.url}"
       |  }
       |  salt = "${ninecards.salt}"
       |  secretKey = "${ninecards.secretKey}"
       |  loaderio {
       |    verificationToken= "$loaderIOToken"
       |  }
       |  webmainpage = "{$webmainpage}"
       |  test {
       |    androidId = "${test.androidId}"
       |    token = "${test.token}"
       |    localization = "${test.localization}"
       |    googlePlayDetailsAppUrl = "${test.googlePlayDetailsAppUrl}"
       |  }
       |}

     """.stripMargin

  def dummyConfig(debugMode: Boolean) = new NineCardsConfig(Option(dummyConfigHocon(debugMode)))

  implicit val config: NineCardsConfiguration = NineCardsConfiguration(
    dummyConfig(debugMode = false))

  val debugConfig: NineCardsConfiguration = NineCardsConfiguration(dummyConfig(debugMode = true))
}
