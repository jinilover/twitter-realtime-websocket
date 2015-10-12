import play.sbt.PlayScala
import sbt._
import sbt.Keys._

object TwitterRealtimeWebSocket extends Build {
  lazy val projectName = "TwitterRealtimeWebSocket"
  lazy val projectV = "1.0"
  lazy val scalaV = "2.11.7"
  lazy val scalazV = "7.1.0"
  lazy val akkaV = "2.3.11"
  lazy val sparkV = "1.4.1"

  lazy val scalaz = Seq(
    "org.scalaz" %% "scalaz-core" % scalazV
  )

  lazy val akka = Seq(
    "com.typesafe.akka" %% "akka-remote" % "2.3.11"
  )

  lazy val spark = Seq(
    "org.apache.spark" %% "spark-core" % sparkV,
    "org.apache.spark" %% "spark-mllib" % sparkV,
    "org.apache.spark" %% "spark-sql" % sparkV,
    "org.apache.spark" %% "spark-streaming" % sparkV,
    "org.apache.spark" %% "spark-streaming-twitter" % sparkV
  )

  lazy val sharedSettings = Seq(
    version := projectV,
    organization := projectName,
    scalaVersion := scalaV
  ) ++ net.virtualvoid.sbt.graph.Plugin.graphSettings

  lazy val commons = Project(
    id = "commons",
    base = file("commons"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings ++
      settings
  ).settings(
    name := "commons",
    libraryDependencies ++= scalaz ++ Seq(
      "com.typesafe" % "config" % "1.3.0"
    )
  )

  lazy val tweetAnalyzer = Project(
    id = "tweetAnalyzer",
    base = file("tweetAnalyzer"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings ++
      settings
  ).settings(
    name := "tweetAnalyzer",
    libraryDependencies ++= akka ++ spark ++ Seq(
      "com.google.code.gson" % "gson" % "2.3",
      "org.twitter4j" % "twitter4j-core" % "3.0.3",
      "commons-cli" % "commons-cli" % "1.2",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"
    )
  ).dependsOn(commons)

  lazy val frontend = Project(
    id = "frontend",
    base = file("frontend"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings ++
      settings
  ).settings(
    name := "frontend",
    libraryDependencies ++= akka
  ).dependsOn(commons)
    .enablePlugins(PlayScala)

}