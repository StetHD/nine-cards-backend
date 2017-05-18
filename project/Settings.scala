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

import sbt._
import sbt.Keys._
import sbtorgpolicies.OrgPoliciesKeys.orgBadgeListSetting
import sbtorgpolicies.OrgPoliciesPlugin
import sbtorgpolicies.OrgPoliciesPlugin.autoImport._
import sbtorgpolicies.model._
import sbtorgpolicies.runnable.SetSetting
import sbtorgpolicies.templates._
import sbtorgpolicies.templates.badges._
import sbtorgpolicies.runnable.syntax._

object ProjectPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  override def requires: Plugins = OrgPoliciesPlugin

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    name := "nine-cards-backend",
    description := "Back-End Server for the 9Cards Android Launcher",
    startYear := Some(2017),
    orgProjectName := "9Cards Backend",
    orgGithubSetting := GitHubSettings(
      organization = "47deg",
      project = (name in LocalRootProject).value,
      organizationName = "47 Degrees",
      groupId = "cards.nine",
      organizationHomePage = url("http://47deg.com"),
      organizationEmail = "hello@47deg.com"
    ),
    orgMaintainersSetting := List(
      Dev("47degfreestyle", Some("47 Degrees (twitter: @47deg)"), Some("hello@47deg.com"))
    ),
    orgBadgeListSetting := List(
      TravisBadge.apply,
      LicenseBadge.apply,
      ScalaLangBadge.apply
    ),
    scalaVersion := "2.11.8",
    scalaOrganization := "org.scala-lang",
    crossScalaVersions := Seq("2.11.8"),
    organization := "com.fortysevendeg",
    organizationName := "47 Degrees",
    organizationHomepage := Some(new URL("http://47deg.com")),
    conflictWarning := ConflictWarning.disable,
    scalacOptions ++= Seq(
      "-Ywarn-unused-import",
      "-Xfatal-warnings"
    ),
    scalacOptions ~= (_ filterNot Set(
      "-Xlint",
      "-Yno-adapted-args",
      "-Ywarn-numeric-widen",
      "-Ywarn-dead-code",
      "-Ywarn-value-discard").contains),
    javaOptions in Test ++= Seq("-XX:MaxPermSize=128m", "-Xms512m", "-Xmx512m"),
    sbt.Keys.fork in Test := false,
    publishMavenStyle := true,
    publishArtifact in (Test, packageSrc) := true,
    logLevel := Level.Info,
    resolvers ++= Seq(
      Resolver.mavenLocal,
      Classpaths.typesafeReleases,
      Resolver.bintrayRepo("scalaz", "releases"),
      DefaultMavenRepository,
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
      "Flyway" at "https://flywaydb.org/repo",
      "RoundEights" at "http://maven.spikemark.net/roundeights"
    ),
    doc in Compile := (target.value / "none")
  )

}

object Settings9C {

  import CustomSettings._
  import com.typesafe.sbt.SbtNativePackager._
  import com.typesafe.sbt.packager.archetypes.JavaAppPackaging.autoImport._
  import org.flywaydb.sbt.FlywayPlugin._
  import org.flywaydb.sbt.FlywayPlugin.autoImport._

  lazy val buildVersion = "1.0.0-SNAPSHOT"

  lazy val flywayTestSettings9C = flywayBaseSettings(Test) ++ Seq(
    flywayDriver := "org.postgresql.Driver",
    flywayUrl := "jdbc:postgresql://localhost/ninecards_test",
    flywayUser := "ninecards_tester",
    flywayPassword := "",
    flywayLocations := Seq("filesystem:" + apiResourcesFolder.value.getPath + "/db/migration")
  )

  lazy val serviceSettings = Seq(
    apiResourcesFolder := apiResourcesFolderDef.value,
    unmanagedClasspath in Test += apiResourcesFolder.value,
    parallelExecution in Test := false,
    test := (test in Test) dependsOn flywayMigrate,
    testOnly := (testOnly in Test) dependsOn flywayMigrate,
    testQuick := (testQuick in Test) dependsOn flywayMigrate
  )

  lazy val googleplaySettings = {
    import sbtprotobuf.{ProtobufPlugin => PB}
    PB.protobufSettings ++ Seq(
      PB.runProtoc in PB.protobufConfig := { args =>
        com.github.os72.protocjar.Protoc.runProtoc("-v261" +: args.toArray)
      }
    )
  }

  lazy val assemblySettings9C = {
    import sbtassembly.AssemblyPlugin._
    import sbtassembly.AssemblyPlugin.autoImport._
    import sbtassembly.MergeStrategy._

    assemblySettings ++ Seq(
      assemblyJarName in assembly := s"9cards-$buildVersion.jar",
      assembleArtifact in assemblyPackageScala := true,
      Keys.test in assembly := {},
      assemblyMergeStrategy in assembly := {
        case "application.conf" => concat
        case "reference.conf"   => concat
        case entry =>
          val oldStrategy   = (assemblyMergeStrategy in assembly).value
          val mergeStrategy = oldStrategy(entry)
          mergeStrategy == deduplicate match {
            case true => first
            case _    => mergeStrategy
          }
      },
      publishArtifact in (Test, packageBin) := false,
      mappings in Universal := {
        val filtered = (mappings in Universal).value.filter {
          case (file, fileName) => !fileName.endsWith(".jar")
        }
        val fatJar = (assembly in Compile).value
        filtered :+ (fatJar, "lib/" + fatJar.getName)
      },
      scriptClasspath := Seq((assemblyJarName in assembly).value)
    )
  }

  lazy val flywaySettings9C =
    flywayBaseSettings(Runtime) ++ Seq(
      flywayDriver := databaseConfig.value.driver,
      flywayUrl := databaseConfig.value.url,
      flywayUser := databaseConfig.value.user,
      flywayPassword := databaseConfig.value.password,
      flywayLocations := Seq("filesystem:" + apiResourcesFolder.value.getPath + "/db/migration")
    )

  lazy val apiSettings = Seq(
    databaseConfig := databaseConfigDef.value,
    apiResourcesFolder := apiResourcesFolderDef.value,
    run := (run in Runtime dependsOn flywayMigrate).evaluated
  ) ++ flywaySettings9C ++ assemblySettings9C
}
