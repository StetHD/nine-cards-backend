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
object Dependencies {

  import sbt._

  def akka(suff: String)  = "com.typesafe.akka" %% s"akka$suff"  % "2.4.8"
  def circe(suff: String) = "io.circe"          %% s"circe$suff" % "0.6.1"
  def doobie(suff: String) = "org.tpolecat" %% s"doobie$suff" % "0.3.0" exclude ("org.scalaz", "*")
  def enumeratum(suff: String) = "com.beachape" %% s"enumeratum$suff" % "1.5.1"
  def http4s(suff: String)     = "org.http4s"   %% s"http4s$suff"     % "0.15.0a"
  def scalaz(suff: String)     = "org.scalaz"   %% s"scalaz$suff"     % "7.2.7"
  def specs2(suff: String)     = "org.specs2"   %% s"specs2$suff"     % "3.8.4" % "test"
  def spray(suff: String)      = "io.spray"     %% s"spray$suff"      % "1.3.3"

  val cats                = "org.typelevel" %% "cats" % "0.8.1"
  val embeddedRedis       = "com.orange.redis-embedded" % "embedded-redis" % "0.6" % "test"
  val flywaydbCore        = "org.flywaydb" % "flyway-core" % "3.2.1"
  val googleApiClient     = "com.google.api-client" % "google-api-client" % "1.20.0" exclude ("com.google.guava", "*")
  val hasher              = "com.roundeights" %% "hasher" % "1.2.0"
  val jodaConvert         = "org.joda" % "joda-convert" % "1.8.1"
  val jodaTime            = "joda-time" % "joda-time" % "2.9.4"
  val mockserver          = "org.mock-server" % "mockserver-netty" % "3.10.4" % "test"
  val newRelic            = "com.newrelic.agent.java" % "newrelic-agent" % "3.29.0"
  val scalacheckDateTime  = "com.fortysevendeg" %% "scalacheck-datetime" % "0.1.0" % "test"
  val scalacheckShapeless = "com.github.alexarchambault" %% "scalacheck-shapeless_1.13" % "1.1.0-RC3" % "test"
  val scredis             = "com.livestream" %% "scredis" % "2.0.6"
  val specs2Core          = specs2("-core") exclude ("org.scalaz", "*")
  val sprayJson           = "io.spray" %% "spray-json" % "1.3.2"
  val sprayTestKit        = spray("-testkit") % "test" exclude ("org.specs2", "specs2_2.11")
  val tagSoup             = "org.ccil.cowan.tagsoup" % "tagsoup" % "1.2.1"
  val typesafeConfig      = "com.typesafe" % "config" % "1.3.0"
}
