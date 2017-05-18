import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import org.flywaydb.sbt.FlywayPlugin
import spray.revolver.RevolverPlugin
import CustomSettings._
import Dependencies._
import Settings9C._

lazy val root = project
  .in(file("."))
  .disablePlugins(FlywayPlugin)
  .aggregate(api, commons, processes, services, googleplay)

lazy val api = project
  .in(file("modules/api"))
  .enablePlugins(JavaAppPackaging )
  .enablePlugins(RevolverPlugin)
  .settings( apiSettings)
  .settings(RevolverPlugin.settings )
  .settings(libraryDependencies ++= Seq(
    %%("akka-actor"),
    akka("-testkit") % "test",
    %%("cats") % "test",
    %%("circe-core"),
    circe("-spray"),
    hasher,
    newRelic,
    %%("scheckShapeless"),
    scalaz("-concurrent"),
    scalaz("-core"),
    specs2("-cats"),
    specs2("-mock"),
    %%("specs2-scalacheck"),
    %%("specs2-core"),
    spray("-can"),
    spray("-routing-shapeless2"),
    sprayJson,
    sprayTestKit
  ))
  .dependsOn(processes, commons)

lazy val commons = project
  .in(file("modules/commons"))
  .disablePlugins(FlywayPlugin)
  .settings(noPublishSettings: _*)
  .settings(libraryDependencies ++= Seq(
    %%("cats"),
    %%("circe-core"),
    %%("circe-generic"),
    %%("circe-parser"),
    embeddedRedis,
    enumeratum(""),
    jodaConvert,
    jodaTime,
    scredis,
    scalaz("-concurrent"),
    %%("specs2-core"),
    %%("specs2-scalacheck"),
    %%("scheckToolboxDatetime"),
    %%("scheckShapeless"),
    typesafeConfig
  ))

lazy val googleplay = project
  .in(file("modules/googleplay"))
  .disablePlugins(FlywayPlugin)
  .settings(noPublishSettings: _*)
  .settings(googleplaySettings)
  .settings(libraryDependencies ++= Seq(
    %%("cats"),
    %%("circe-core"),
    %%("circe-generic"),
    %%("circe-parser"),
    embeddedRedis,
    enumeratum(""),
    enumeratum("-circe"),
    %%("http4s-blaze-client"),
    %%("joda-convert"),
    %%("joda-time"),
    mockserver,
    newRelic,
    scredis,
    %%("scheckShapeless"),
    %%("specs2-core"),
    specs2("-matcher-extra"),
    specs2("-mock"),
    %%("specs2-scalacheck"),
    %%("specs2-scalacheck"),
    tagSoup
  ))
  .dependsOn(commons % "compile -> compile; test -> test")

lazy val services = project
  .in(file("modules/services"))
  .disablePlugins(FlywayPlugin)
  .settings(noPublishSettings: _*)
  .settings(serviceSettings)
  .settings(flywayTestSettings9C)
  .settings(libraryDependencies ++= Seq(
    %%("cats"),
    %%("circe-core"),
    %%("circe-generic"),
    %%("doobie-core"),
    %%("doobie-h2"),
    %%("doobie-hikari"),
    %%("doobie-postgres"),
    %%("doobie-specs2") % "test",
    enumeratum(""),
    enumeratum("-circe"),
    flywaydbCore % "test",
    googleApiClient,
    hasher,
    %%("http4s-blaze-client"),
    %%("http4s-circe"),
    %%("joda-convert"),
    %%("joda-time"),
    mockserver,
    %%("scheckShapeless"),
    %%("scalaz-concurrent"),
    scalaz("-core"),
    specs2("-cats"),
    specs2("-mock"),
    %%("specs2-scalacheck"),
    %%("specs2-core")
  ))
  .dependsOn(googleplay, commons % "compile -> compile; test -> test")

lazy val processes = project
  .in(file("modules/processes"))
  .disablePlugins(FlywayPlugin)
  .settings(noPublishSettings: _*)
  .settings(
    Seq(
      apiResourcesFolder := apiResourcesFolderDef.value,
      unmanagedClasspath in Test += apiResourcesFolder.value
    ))
  .settings(libraryDependencies ++= Seq(
    hasher,
    %%("scheckShapeless"),
    %%("scalaz-concurrent"),
    scalaz("-core"),
    specs2("-cats"),
    %%("specs2-core"),
    specs2("-mock"),
    %%("specs2-scalacheck")
  ))
  .dependsOn(services, commons)

lazy val tests = Project(id = "tests", base = file("modules/tests"))
  .disablePlugins(FlywayPlugin)
  .settings(noPublishSettings: _*)
  .aggregate(api, commons, processes, services, googleplay)

pgpPassphrase := Some(getEnvVar("PGP_PASSPHRASE").getOrElse("").toCharArray)
pgpPublicRing := file(s"$gpgFolder/pubring.asc")
pgpSecretRing := file(s"$gpgFolder/secring.asc")
