name := "TMDb-shelf"

organization := "org.edla"

version := "0.18"

jfxSettings

JFX.mainClass := Some("org.edla.tmdb.shelf.Launcher")

JFX.nativeBundles := "all"

//required for Windows sbt package-javafx
//JFX.devKit := JFX.jdk("C:/Program Files/Java/jdk1.8.0_66")

scalaVersion := "2.11.7"

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
  "-Xfuture"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.1",
  "org.scala-lang.modules" %% "scala-async" % "0.9.5",
  "org.edla" %% "tmdb-async-client" % "1.0.2",
  "com.typesafe.slick" %% "slick" % "3.1.0",
  "com.h2database" % "h2" % "1.4.190",
  "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.16",
  "org.apache.commons" % "commons-lang3" % "3.4"
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

//conflictWarning := ConflictWarning.disable

