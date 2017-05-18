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

import java.sql.Timestamp

sealed abstract class BaseSharedCollection {
  def sharedCollectionId: Long
}

case class SharedCollection(
    id: Long,
    publicIdentifier: String,
    userId: Option[Long],
    publishedOn: Timestamp,
    author: String,
    name: String,
    views: Int,
    category: String,
    icon: String,
    community: Boolean,
    packages: List[String]
) extends BaseSharedCollection {
  override def sharedCollectionId: Long = id
}

case class SharedCollectionWithAggregatedInfo(
    sharedCollectionData: SharedCollection,
    subscriptionsCount: Long
) extends BaseSharedCollection {
  override def sharedCollectionId: Long = sharedCollectionData.id
}

case class SharedCollectionSubscription(
    sharedCollectionId: Long,
    userId: Long,
    sharedCollectionPublicId: String
)

object SharedCollection {
  val fields = List(
    "publicidentifier",
    "userid",
    "publishedon",
    "author",
    "name",
    "views",
    "category",
    "icon",
    "community",
    "packages")
  val allFields = "id" +: fields

  val insertFields    = fields.mkString(",")
  val insertWildCards = fields.map(_ ⇒ "?").mkString(",")

  object Queries {
    val getById               = "select * from sharedcollections where id=?"
    val getByPublicIdentifier = "select * from sharedcollections where publicidentifier=?"
    val getByUser =
      s"""
        |select C.*, count(S.*) as subscriptionCount
        |from sharedcollections as C
        |left join sharedcollectionsubscriptions as S on C.id=S.sharedcollectionid
        |where C.userid=?
        |group by C.id""".stripMargin
    val getLatestByCategory =
      "select * from sharedcollections where category=? order by publishedon desc limit ? offset ?"
    val getTopByCategory =
      "select * from sharedcollections where category=? order by views desc limit ? offset ?"
    val insert             = s"insert into sharedcollections($insertFields) values($insertWildCards)"
    val update             = "update sharedcollections set name=? where id=?"
    val updatePackages     = "update sharedcollections set packages=? where id=?"
    val increaseViewsByOne = "update sharedcollections set views=views+1 where id=?"
  }
}

object SharedCollectionSubscription {
  val fields = List("sharedcollectionid", "userid", "sharedcollectionpublicid")

  val insertFields    = fields.mkString(",")
  val insertWildCards = fields.map(_ ⇒ "?").mkString(",")

  object Queries {
    val getByCollection = "select * from sharedcollectionsubscriptions where sharedcollectionid=?"
    val getByCollectionAndUser =
      "select * from sharedcollectionsubscriptions where sharedcollectionid=? and userid=?"
    val getByUser = "select * from sharedcollectionsubscriptions where userid=?"
    val insert =
      s"insert into sharedcollectionsubscriptions($insertFields) values($insertWildCards)"
    val deleteByCollectionAndUser =
      "delete from sharedcollectionsubscriptions where sharedcollectionid=? and userid=?"
  }
}
