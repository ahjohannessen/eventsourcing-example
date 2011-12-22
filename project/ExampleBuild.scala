import sbt._
import Keys._

object BuildSettings {
  val buildOrganization = "dev.example"
  val buildVersion      = "0.1-SNAPSHOT"
  val buildScalaVersion = "2.9.1"

  val buildSettings = Defaults.defaultSettings ++ Seq (
    organization := buildOrganization,
    version      := buildVersion,
    scalaVersion := buildScalaVersion
  )
}

object Resolvers {
  val typesafeRepo  = "Typesafe Repo"   at "http://repo.typesafe.com/typesafe/releases/"
  val journalioRepo = "Journal.IO Repo" at "https://raw.github.com/sbtourist/Journal.IO/master/m2/repo"
}

object Versions {
  val Akka = "1.2"
  val Jersey = "1.9.1"
  val Jetty = "8.0.4.v20111024"
  val Netty = "3.2.5.Final"
}

object Dependencies {
  import Versions._

  // compile dependencies
  lazy val akkaActor    = "se.scalablesolutions.akka"  % "akka-actor"        % Akka    % "compile"
  lazy val akkaStm      = "se.scalablesolutions.akka"  % "akka-stm"          % Akka    % "compile"
  lazy val journalio    = "journalio"                  % "journalio"         % "1.0"   % "compile"
  lazy val jsr311       = "javax.ws.rs"                % "jsr311-api"        % "1.1.1" % "compile"
  lazy val jerseyCore   = "com.sun.jersey"             % "jersey-core"       % Jersey  % "compile"
  lazy val jerseyJson   = "com.sun.jersey"             % "jersey-json"       % Jersey  % "compile"
  lazy val jerseyServer = "com.sun.jersey"             % "jersey-server"     % Jersey  % "compile"
  lazy val netty        = "org.jboss.netty"            % "netty"             % Netty   % "compile"
  lazy val scalate      = "org.fusesource.scalate"     % "scalate-core"      % "1.5.2" % "compile"
  lazy val scalaz       = "org.scalaz"                 % "scalaz-core_2.9.1" % "6.0.3" % "compile"
  lazy val zookeeper    = "org.apache.zookeeper"       % "zookeeper"         % "3.3.3" % "compile"

  // container dependencies TODO: switch from "compile" to "container" when using xsbt-web-plugin
  lazy val jettyServer  = "org.eclipse.jetty"          % "jetty-server"      % Jetty   % "compile"
  lazy val jettyServlet = "org.eclipse.jetty"          % "jetty-servlet"     % Jetty   % "compile"
  lazy val jettyWebapp  = "org.eclipse.jetty"          % "jetty-webapp"      % Jetty   % "compile"

  // runtime dependencies
  lazy val configgy  = "net.lag" % "configgy" % "2.0.0" % "runtime"

  // test dependencies
  lazy val scalatest = "org.scalatest" % "scalatest_2.9.1" % "1.6.1" % "test"
}

object ExampleBuild extends Build {
  import BuildSettings._
  import Resolvers._
  import Dependencies._

  lazy val example = Project (
    "eventsourcing-example",
    file("."),
    settings = buildSettings ++ Seq (
      resolvers            := Seq (typesafeRepo, journalioRepo),
      // compile dependencies (backend)
      libraryDependencies ++= Seq (akkaActor, akkaStm, journalio, netty, scalaz, zookeeper),
      // compile dependencies (frontend)
      libraryDependencies ++= Seq (jsr311, jerseyCore, jerseyJson, jerseyServer, scalate),
      // container dependencies
      libraryDependencies ++= Seq (jettyServer, jettyServlet, jettyWebapp),
      // runtime dependencies
      libraryDependencies ++= Seq (configgy),
      // test dependencies
      libraryDependencies ++= Seq (scalatest),
      // execute tests sequentially
      parallelExecution in Test := false
    )
  )
}