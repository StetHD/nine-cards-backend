package com.fortysevendeg.ninecards.services.persistence

import doobie.imports._
import shapeless.HNil

import scalaz.Foldable

class PersistenceImpl {

  def fetchList[K](
    sql: String)(implicit ev: Composite[K]): ConnectionIO[List[K]] =
    Query[HNil, K](sql).toQuery0(HNil).to[List]

  def fetchList[A, K](
    sql: String,
    values: A)(implicit ev: Composite[A], ev2: Composite[K]): ConnectionIO[List[K]] =
    Query[A, K](sql).to[List](values)

  def fetchOption[A, K](
    sql: String,
    values: A)(implicit ev: Composite[A], ev2: Composite[K]): ConnectionIO[Option[K]] =
    Query[A, K](sql).option(values)

  def fetchUnique[A, K](
    sql: String,
    values: A)(implicit ev: Composite[A], ev2: Composite[K]): ConnectionIO[K] =
    Query[A, K](sql).unique(values)

  def update(
    sql: String): ConnectionIO[Int] =
    Update[HNil](sql).run(HNil)

  def update[A](
    sql: String,
    values: A)(implicit ev: Composite[A]): ConnectionIO[Int] =
    Update[A](sql).run(values)

  def updateWithGeneratedKeys[A, K](
    sql: String,
    fields: List[String],
    values: A)(implicit ev: Composite[A], ev2: Composite[K]): ConnectionIO[K] =
    Update[A](sql).withUniqueGeneratedKeys[K](fields: _*)(values)

  def updateMany[F[_], A](
    sql: String,
    values: F[A])(implicit ev: Composite[A], F: Foldable[F]): ConnectionIO[Int] =
    Update[A](sql).updateMany(values)

}

object PersistenceImpl {

  implicit def persistenceImpl: PersistenceImpl = new PersistenceImpl
}
