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

package cards.nine.googleplay.service.free.interpreter.cache

import cards.nine.commons.redis._
import cards.nine.domain.application.Package
import cards.nine.googleplay.service.free.algebra.Cache._
import cats.~>
import cats.instances.list._
import cats.syntax.cartesian._
import cats.syntax.functor._
import cats.syntax.traverse._
import org.joda.time.{DateTime, DateTimeZone}
import scala.concurrent.ExecutionContext

class CacheInterpreter(implicit ec: ExecutionContext) extends (Ops ~> RedisOps) {

  import Formats._
  import RedisOps._

  private[this] val wrap: CacheWrapper[CacheKey, CacheVal] = new CacheWrapper

  private[this] val errorCache: CacheQueue[CacheKey, DateTime] = new CacheQueue

  private[this] val pendingSet: CacheSet[PendingQueueKey.type, Package] = new CacheSet(
    PendingQueueKey)

  def apply[A](ops: Ops[A]): RedisOps[A] = ops match {

    case GetValid(pack) ⇒
      val keys = List(CacheKey.resolved(pack), CacheKey.permanent(pack))
      wrap.mget(keys).map(_.flatMap(_.card).headOption)

    case GetValidMany(packages) ⇒
      val keys = (packages map CacheKey.resolved) ++ (packages map CacheKey.permanent)
      wrap.mget(keys).map(_.flatMap(_.card))

    case PutResolved(card) ⇒
      val pack = card.packageName
      pendingSet.remove(pack) *> removeError(pack) *> wrap.put(CacheEntry.resolved(card))

    case PutResolvedMany(cards) ⇒
      val packs = cards.map(_.packageName)
      wrap.mput(cards map CacheEntry.resolved) *> removeErrorMany(packs) *> pendingSet.remove(
        packs)

    case PutPermanent(card) ⇒
      val pack = card.packageName
      pendingSet.remove(pack) *> removeError(pack) *> wrap.put(CacheEntry.permanent(card))

    case SetToPending(pack) ⇒
      removeError(pack) *> pendingSet.insert(pack)

    case SetToPendingMany(packages) ⇒
      removeErrorMany(packages) *> pendingSet.insert(packages)

    case AddError(pack) ⇒
      val now = DateTime.now(DateTimeZone.UTC)
      pendingSet.remove(pack) *> errorCache.enqueue(CacheKey.error(pack), now)

    case AddErrorMany(packages) ⇒
      val now = DateTime.now(DateTimeZone.UTC)
      val putErrors = packages.traverse[RedisOps, Unit] { pack ⇒
        errorCache.enqueue(CacheKey.error(pack), now)
      }
      putErrors *> pendingSet.remove(packages)

    case ListPending(num) ⇒ pendingSet.extractMany(num)
  }

  private[this] def removeError(pack: Package): RedisOps[Unit] =
    errorCache.delete(CacheKey.error(pack))

  private[this] def removeErrorMany(packages: List[Package]): RedisOps[Unit] =
    errorCache.delete(packages map CacheKey.error)

}
