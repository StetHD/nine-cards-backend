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
  .settings(
    apiSettings ++ RevolverPlugin.settings ++ Seq(libraryDependencies ++= apiDeps)
  )
  .dependsOn(processes, commons)

lazy val commons = project
  .in(file("modules/commons"))
  .disablePlugins(FlywayPlugin)
  .settings(noPublishSettings: _*)
  .settings(libraryDependencies ++= commonsDeps)

lazy val googleplay = project
  .in(file("modules/googleplay"))
  .disablePlugins(FlywayPlugin)
  .settings(noPublishSettings: _*)
  .settings(googleplaySettings)
  .settings(libraryDependencies ++= googleplayDeps)
  .dependsOn(commons % "compile -> compile; test -> test")

lazy val services = project
  .in(file("modules/services"))
  .disablePlugins(FlywayPlugin)
  .settings(serviceSettings)
  .settings(flywayTestSettings9C)
  .settings(libraryDependencies ++= servicesDeps)
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
  .settings(libraryDependencies ++= baseDeps)
  .dependsOn(services, commons)

lazy val tests = Project(id = "tests", base = file("modules/tests"))
  .disablePlugins(FlywayPlugin)
  .settings(noPublishSettings: _*)
  .aggregate(api, commons, processes, services, googleplay)

pgpPassphrase := Some(getEnvVar("PGP_PASSPHRASE").getOrElse("").toCharArray)
pgpPublicRing := file(s"$gpgFolder/pubring.asc")
pgpSecretRing := file(s"$gpgFolder/secring.asc")
