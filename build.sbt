enablePlugins(JavaFxPlugin)

name := "TMDb-shelf"
organization := "org.edla"
version := "1.2.2"

//sbt javaFxPackage
//for macOS used only to produce the jar, jpackage produce more compact App
javaFxMainClass := "org.edla.tmdb.shelf.Main"
javaFxVerbose := true

scalaVersion in ThisBuild := "2.13.1"

scalacOptions ++= Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-encoding",
  "utf-8",                         // Specify character encoding used by source files.
  "-explaintypes",                 // Explain type errors in more detail.
  "-feature",                      // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials",        // Existential types (besides wildcard types) can be written and inferred
  "-language:higherKinds",         // Allow higher-kinded types
  "-language:implicitConversions", // Allow definition of implicit functions called views
  "-unchecked",                    // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit",                   // Wrap field accessors to throw an exception on uninitialized access.
  //"-Xfatal-warnings",              // Fail the compilation if there are any warnings.
  "-Xlint:adapted-args",           // Warn if an argument list is modified to match the receiver.
  "-Xlint:constant",               // Evaluation of a constant arithmetic expression results in an error.
  "-Xlint:delayedinit-select",     // Selecting member of DelayedInit.
  "-Xlint:doc-detached",           // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible",           // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any",              // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator",   // A string literal appears to be missing an interpolator id.
  "-Xlint:nullary-override",       // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Xlint:nullary-unit",           // Warn when nullary methods return Unit.
  "-Xlint:option-implicit",        // Option.apply used implicit view.
  "-Xlint:package-object-classes", // Class or object defined in package object.
  "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow",         // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align",            // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow",  // A local type parameter shadows a type already in scope.
  // "-Yno-imports",                      // No predef or default imports
  "-Ywarn-dead-code",        // Warn when dead code is identified.
  "-Ywarn-extra-implicit",   // Warn when more than one implicit parameter section is defined.
  "-Ywarn-numeric-widen",    // Warn when numerics are widened.
  "-Ywarn-unused:implicits", // Warn if an implicit parameter is unused.
  "-Ywarn-unused:imports",   // Warn if an import selector is not referenced.
  "-Ywarn-unused:locals",    // Warn if a local definition is unused.
  "-Ywarn-unused:params",    // Warn if a value parameter is unused.
  "-Ywarn-unused:patvars",   // Warn if a variable bound in a pattern is unused.
  "-Ywarn-unused:privates",  // Warn if a private member is unused.
  "-Ywarn-value-discard"     // Warn when non-Unit expression results are unused.
)

val javafxModules = Seq("base", "controls", "fxml", "graphics") //, "media", "swing", "web")
val osName = System.getProperty("os.name") match {
  case n if n.startsWith("Linux")   => "linux"
  case n if n.startsWith("Mac")     => "mac"
  case n if n.startsWith("Windows") => "win"
  case _                            => throw new Exception("Unknown platform!")
}

libraryDependencies ++= javafxModules.map(m => "org.openjfx" % s"javafx-$m" % "13" classifier osName)
libraryDependencies ++= Seq(
  "com.typesafe.akka"           %% "akka-actor"                 % "2.5.25",
  "org.scala-lang.modules"      %% "scala-async"                % "0.10.0",
  "org.scala-lang.modules"      %% "scala-parallel-collections" % "0.2.0",
  "org.edla"                    %% "tmdb-async-client"          % "2.1.0",
  "com.typesafe.slick"          %% "slick"                      % "3.3.2",
  "com.h2database"              % "h2"                          % "1.4.197", //1.4.199 crash
  "net.sourceforge.htmlcleaner" % "htmlcleaner"                 % "2.23",
  "org.scala-lang.modules"      %% "scala-java8-compat"         % "0.9.0",
  "me.xdrop"                    % "fuzzywuzzy"                  % "1.2.0",
  "org.scalatest"               %% "scalatest"                  % "3.0.8" % "test"
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

assemblyMergeStrategy in assembly := {
  case "module-info.class" => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
