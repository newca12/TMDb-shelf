name := "TMDb-shelf"
organization := "org.edla"
version := "0.28"

//sbt jdkPackager:packageBin
mainClass in Compile := Some("org.edla.tmdb.shelf.Launcher")
enablePlugins(JDKPackagerPlugin)
jdkPackagerType := "installer"

(antPackagerTasks in JDKPackager) := (antPackagerTasks in JDKPackager).value

scalaVersion := "2.12.1"
scalacOptions ++= Seq(
  "-language:postfixOps",
  "-language:existentials",
  "-language:implicitConversions",
  //"-optimize",
  "-deprecation",
  "-encoding", // yes this
  "UTF-8", // is 2 args
  "-feature",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint",
  "-Yno-adapted-args",
  //"-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture"
)
scalafmtConfig in ThisBuild := Some(file(".scalafmt.conf"))

libraryDependencies ++= Seq(
  "com.typesafe.akka"           %% "akka-actor"         % "2.4.14",
  "org.scala-lang.modules"      %% "scala-async"        % "0.9.6",
  "org.edla"                    %% "tmdb-async-client"  % "1.1.0",
  "com.typesafe.slick"          %% "slick"              % "3.2.0-M2",
  "com.h2database"              % "h2"                  % "1.4.193",
  "net.sourceforge.htmlcleaner" % "htmlcleaner"         % "2.18",
  "org.scala-lang.modules"      %% "scala-java8-compat" % "0.8.0",
  "org.scalatest"               %% "scalatest"          % "3.0.1" % "test"
)

lazy val root = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "org.edla.tmdb.shelf",
    buildInfoUsePackageAsPath := true
  )

fork := true
licenses := Seq("GNU GPL v3" -> url("http://www.gnu.org/licenses/gpl.html"))
homepage := Some(url("http://github.com/newca12/TMDb-shelf"))
