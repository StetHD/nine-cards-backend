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

package cards.nine.services.free.domain

import cards.nine.domain.account.DeviceToken
import cards.nine.domain.application.Package

object Firebase {

  case class UpdatedCollectionNotificationInfo(
      deviceTokens: List[DeviceToken],
      publicIdentifier: String,
      packagesName: List[Package]
  )

  case class SendNotificationRequest[T](
      registration_ids: List[DeviceToken],
      data: SendNotificationPayload[T]
  )

  case class SendNotificationPayload[T](
      payloadType: String,
      payload: T
  )

  case class UpdateCollectionNotificationPayload(
      publicIdentifier: String,
      addedPackages: List[Package]
  )

  case class SendNotificationResponse(
      multicastIds: List[Long],
      success: Int,
      failure: Int,
      canonicalIds: Int,
      results: List[NotificationIndividualResult]
  )

  object SendNotificationResponse {
    val emptyResponse = SendNotificationResponse(Nil, 0, 0, 0, Nil)
  }

  case class NotificationResponse(
      multicast_id: Long,
      success: Int,
      failure: Int,
      canonical_ids: Int,
      results: Option[List[NotificationIndividualResult]]
  )

  case class NotificationIndividualResult(
      message_id: Option[String],
      registration_id: Option[String],
      error: Option[String]
  )

}
