name := "TMDb-shelf"
organization := "org.edla"
version := "0.25"

//sbt jdkPackager:packageBin
mainClass in Compile := Some("org.edla.tmdb.shelf.Launcher")
enablePlugins(JDKPackagerPlugin)
jdkPackagerType := "installer"

(antPackagerTasks in JDKPackager) := (antPackagerTasks in JDKPackager).value

scalaVersion := "2.11.8"
scalacOptions ++= Seq(
  "-language:postfixOps", "-language:existentials", "-language:implicitConversions",
  //"-optimize",
  "-deprecation",
  "-encoding", "UTF-8", // yes, this is 2 args
  "-feature",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Ywarn-unused-import",
  "-Xfuture",
  "-Ybackend:GenBCode",
  "-Ydelambdafy:method",
  "-target:jvm-1.8"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.2",
  "org.scala-lang.modules" %% "scala-async" % "0.9.5",
  "org.edla" %% "tmdb-async-client" % "1.0.6",
  "com.typesafe.slick" %% "slick" % "3.1.1",
  "com.h2database" % "h2" % "1.4.191",
  "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.16",
  "org.scala-lang.modules" %% "scala-java8-compat" % "0.7.0",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test"
)

lazy val root = (project in file(".")).
  enablePlugins(BuildInfoPlugin).
  settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "org.edla.tmdb.shelf"
  )

fork := true
licenses := Seq("GNU GPL v3" -> url("http://www.gnu.org/licenses/gpl.html"))
homepage := Some(url("http://github.com/newca12/TMDb-shelf"))
