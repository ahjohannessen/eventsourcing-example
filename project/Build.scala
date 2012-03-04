import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {
  val appVersion      = "0.1-SNAPSHOT"
  val appName         = "eventsourcing-example"
  val subName         = "service"

  val typesafeRepo  = "Typesafe Repo"  at "http://repo.typesafe.com/typesafe/releases/"
  val journalioRepo = "Journalio Repo" at "https://raw.github.com/sbtourist/Journal.IO/master/m2/repo"

  // Versions
  val Akka   = "2.0-RC3"
  val Jersey = "1.9.1"
  val Netty  = "3.2.5.Final"

  val appDependencies = Nil
  val subDependencies = Seq(
    // compile dependencies
    "com.sun.jersey"             % "jersey-core"       % Jersey      % "compile",
    "com.sun.jersey"             % "jersey-json"       % Jersey      % "compile",
    "com.sun.jersey"             % "jersey-server"     % Jersey      % "compile",
    "com.typesafe.akka"          % "akka-actor"        % Akka        % "compile",
    "com.typesafe.akka"          % "akka-agent"        % Akka        % "compile",
    "journalio"                  % "journalio"         % "1.0"       % "compile",
    "org.jboss.netty"            % "netty"             % Netty       % "compile",
    "org.scalaz"                 % "scalaz-core_2.9.1" % "6.0.3"     % "compile",
    "org.apache.zookeeper"       % "zookeeper"         % "3.3.3"     % "compile" excludeAll(
      ExclusionRule(organization = "com.sun.jdmk"),
      ExclusionRule(organization = "com.sun.jmx"),
      ExclusionRule(organization = "javax.jms")
    ),

    // test dependencies
    "org.scalatest"              % "scalatest_2.9.1"   % "1.6.1"     % "test"
  )

  val sub = Project(subName, file("modules/%s" format subName)).settings(
    resolvers           ++= Seq(typesafeRepo, journalioRepo),
    libraryDependencies ++= subDependencies
  )

  val app = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA)
    .dependsOn(sub)
    .aggregate(sub)
    .settings()
}
