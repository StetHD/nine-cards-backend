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
    akka("-actor"),
    akka("-testkit") % "test",
    cats % "test",
    circe("-core"),
    circe("-spray"),
    hasher,
    newRelic,
    scalacheckShapeless,
    scalaz("-concurrent"),
    scalaz("-core"),
    specs2("-cats"),
    specs2("-mock"),
    specs2("-scalacheck"),
    specs2Core,
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
    cats,
    circe("-core"),
    circe("-generic"),
    circe("-parser"),
    embeddedRedis,
    enumeratum(""),
    jodaConvert,
    jodaTime,
    scredis,
    scalaz("-concurrent"),
    specs2Core,
    specs2("-scalacheck"),
    scalacheckDateTime,
    scalacheckShapeless,
    typesafeConfig
  ))

lazy val googleplay = project
  .in(file("modules/googleplay"))
  .disablePlugins(FlywayPlugin)
  .settings(noPublishSettings: _*)
  .settings(googleplaySettings)
  .settings(libraryDependencies ++= Seq(
    cats,
    circe("-core"),
    circe("-generic"),
    circe("-parser"),
    embeddedRedis,
    enumeratum(""),
    enumeratum("-circe"),
    http4s("-blaze-client"),
    jodaConvert,
    jodaTime,
    mockserver,
    newRelic,
    scredis,
    scalacheckShapeless,
    specs2Core,
    specs2("-matcher-extra"),
    specs2("-mock"),
    specs2("-scalacheck"),
    tagSoup
  ))
  .dependsOn(commons % "compile -> compile; test -> test")

lazy val services = project
  .in(file("modules/services"))
  .disablePlugins(FlywayPlugin)
  .settings(serviceSettings)
  .settings(flywayTestSettings9C)
  .settings(libraryDependencies ++= Seq(
    cats,
    circe("-core"),
    circe("-generic"),
    doobie("-contrib-h2"),
    doobie("-contrib-hikari"),
    doobie("-contrib-postgresql"),
    doobie("-contrib-specs2") % "test",
    doobie("-core"),
    enumeratum(""),
    enumeratum("-circe"),
    flywaydbCore % "test",
    googleApiClient,
    hasher,
    http4s("-blaze-client"),
    http4s("-circe"),
    jodaConvert,
    jodaTime,
    mockserver,
    scalacheckShapeless,
    scalaz("-concurrent"),
    scalaz("-core"),
    specs2("-cats"),
    specs2("-mock"),
    specs2("-scalacheck"),
    specs2Core
  ))
  .settings(noPublishSettings: _*)
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
    scalacheckShapeless,
    scalaz("-concurrent"),
    scalaz("-core"),
    specs2("-cats"),
    specs2Core,
    specs2("-mock"),
    specs2("-scalacheck")
  ))
  .dependsOn(services, commons)

lazy val tests = Project(id = "tests", base = file("modules/tests"))
  .disablePlugins(FlywayPlugin)
  .settings(noPublishSettings: _*)
  .aggregate(api, commons, processes, services, googleplay)

pgpPassphrase := Some(getEnvVar("PGP_PASSPHRASE").getOrElse("").toCharArray)
pgpPublicRing := file(s"$gpgFolder/pubring.asc")
pgpSecretRing := file(s"$gpgFolder/secring.asc")
