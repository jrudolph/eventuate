import Keys.{compile => comp, _}

val akkaVersion = "2.4.1"

val protobufVersion = "2.5.0"

organization := "com.rbmhtechnology"

name := "eventuate"

version := "0.6-SNAPSHOT"

scalaVersion := "2.11.7"

scalacOptions in (Compile, doc) := List("-skip-packages", "akka")

scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation", "-Xlint")

fork in Test := true

parallelExecution in Test := false

connectInput in run := true

resolvers += "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"

libraryDependencies ++= Seq(
  "com.datastax.cassandra"           % "cassandra-driver-core"         % "2.1.5",
  "com.google.guava"                 % "guava"                         % "16.0",
  "com.google.protobuf"              % "protobuf-java"                 % protobufVersion,
  "com.javaslang"                    % "javaslang"                     % "2.0.0-RC3"  % "test,it",
  "com.novocode"                     % "junit-interface"               % "0.11"       % "test->default",
  "com.typesafe.akka"               %% "akka-remote"                   % akkaVersion,
  "com.typesafe.akka"               %% "akka-testkit"                  % akkaVersion  % "test,it",
  "com.typesafe.akka"               %% "akka-multi-node-testkit"       % akkaVersion  % "test",
  "commons-io"                       % "commons-io"                    % "2.4",
  "org.cassandraunit"                % "cassandra-unit"                % "2.0.2.2"    % "test,it" excludeAll ExclusionRule(organization = "ch.qos.logback"),
  "org.fusesource.leveldbjni"        % "leveldbjni-all"                % "1.8",
  "org.scalatest"                   %% "scalatest"                     % "2.1.4"      % "test,it",
  "org.scalaz"                      %% "scalaz-core"                   % "7.1.0",
  "org.scala-lang.modules"           % "scala-java8-compat_2.11"       % "0.7.0"
)

// ----------------------------------------------------------------------
//  Database replication POC (example)
// ----------------------------------------------------------------------

val springVersion = "4.2.4.RELEASE"

libraryDependencies ++= Seq(
  "org.aspectj" % "aspectjrt" % "1.8.8",
  "org.aspectj" % "aspectjweaver" % "1.8.8",
  "org.springframework" % "spring-context" % springVersion,
  "org.springframework" % "spring-jdbc" % springVersion,
  "org.springframework" % "spring-tx" % springVersion,
  "org.hsqldb" % "hsqldb" % "2.3.3"
)

// ----------------------------------------------------------------------
//  Documentation
// ----------------------------------------------------------------------

site.settings

site.sphinxSupport()

site.includeScaladoc()

ghpages.settings

git.remoteRepo := "git@github.com:RBMHTechnology/eventuate.git"

unmanagedSourceDirectories in Test += baseDirectory.value / "src" / "sphinx"/ "code"

// ----------------------------------------------------------------------
//  Publishing
// ----------------------------------------------------------------------

credentials += Credentials(
  "Artifactory Realm",
  "oss.jfrog.org",
  sys.env.getOrElse("OSS_JFROG_USER", ""),
  sys.env.getOrElse("OSS_JFROG_PASS", "")
)

publishTo := {
  val jfrog = "https://oss.jfrog.org/artifactory/"
  if (isSnapshot.value)
    Some("OJO Snapshots" at jfrog + "oss-snapshot-local")
  else
    Some("OJO Releases" at jfrog + "oss-release-local")
}

publishMavenStyle := true

// ----------------------------------------------------------------------
//  Protobuf compilation
// ----------------------------------------------------------------------

protobufSettings

version in protobufConfig := "2.5.0"
runProtoc in protobufConfig := (args =>
  com.github.os72.protocjar.Protoc.runProtoc("-v250" +: args.toArray)
)

// ----------------------------------------------------------------------
//  Integration and Multi-JVM testing
// ----------------------------------------------------------------------

import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

lazy val IntegrTest = config("it") extend(Test)

evictionWarningOptions in update := EvictionWarningOptions.default
  .withWarnTransitiveEvictions(false)
  .withWarnDirectEvictions(false)
  .withWarnScalaVersionEviction(false)

lazy val root = (project in file("."))
  .configs(IntegrTest)
  .settings(itSettings: _*)
  .configs(MultiJvm)
  .settings(multiJvmSettings: _*)

lazy val itSettings = Defaults.itSettings ++ Seq(
  comp in IntegrTest <<= (comp in IntegrTest) triggeredBy (comp in Test),
  test in IntegrTest <<= (test in IntegrTest) triggeredBy (test in Test),
  parallelExecution in IntegrTest := false,
  fork in IntegrTest := true
)

lazy val multiJvmSettings = SbtMultiJvm.multiJvmSettings ++ Seq(
  comp in MultiJvm <<= (comp in MultiJvm) triggeredBy (comp in Test),
  parallelExecution in Test := false,
  executeTests in Test <<=
    ((executeTests in Test), (executeTests in MultiJvm)) map {
      case ((testResults), (multiJvmResults)) =>
        val overall =
          if (testResults.overall.id < multiJvmResults.overall.id) multiJvmResults.overall
          else testResults.overall
        Tests.Output(overall,
          testResults.events ++ multiJvmResults.events,
          testResults.summaries ++ multiJvmResults.summaries)
    }
)

// ----------------------------------------------------------------------
//  Example classpath generation
// ----------------------------------------------------------------------

lazy val exampleClasspath = taskKey[Unit]("generate example classpath script")

exampleClasspath <<= exampleClasspath.dependsOn(comp in Test)

exampleClasspath := {
  import java.nio.file.{Paths, Files, StandardOpenOption}
  val cpaths = (fullClasspath in Test value) map (_.data) mkString(":")
  val output =
    s"""|#!/bin/sh
        |
        |# this file is automatically generated by the sbt 'exampleClasspath' command
        |
        |export EXAMPLE_CLASSPATH="$cpaths"
        |""".stripMargin

  val fileName = "example/.example-classpath"
  val file = Paths.get(fileName)

  Files.write(file, output.getBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
  file.toFile.setExecutable(true)
}

// ----------------------------------------------------------------------
//  Source code formatting
// ----------------------------------------------------------------------

com.rbmhtechnology.eventuate.Formatting.formatSettings

// ----------------------------------------------------------------------
//  File headers
// ----------------------------------------------------------------------

val header = (HeaderPattern.cStyleBlockComment,
  """|/*
     | * Copyright (C) 2015 - 2016 Red Bull Media House GmbH <http://www.redbullmediahouse.com> - all rights reserved.
     | *
     | * Licensed under the Apache License, Version 2.0 (the "License");
     | * you may not use this file except in compliance with the License.
     | * You may obtain a copy of the License at
     | *
     | * http://www.apache.org/licenses/LICENSE-2.0
     | *
     | * Unless required by applicable law or agreed to in writing, software
     | * distributed under the License is distributed on an "AS IS" BASIS,
     | * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     | * See the License for the specific language governing permissions and
     | * limitations under the License.
     | */
     |
     |""".stripMargin)

headers := Map(
  "scala" -> header,
  "java"  -> header)

inConfig(IntegrTest)(SbtHeader.toBeScopedSettings)
inConfig(MultiJvm)(SbtHeader.toBeScopedSettings)

val createHeaderDep = compileInputs.in(comp) <<= compileInputs.in(comp).dependsOn(createHeaders.in(comp))

inConfig(Compile)(createHeaderDep)
inConfig(Test)(createHeaderDep)
inConfig(IntegrTest)(createHeaderDep)
inConfig(MultiJvm)(createHeaderDep)