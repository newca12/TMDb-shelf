name := "TMDb-shelf"

organization := "org.edla"

version := "0.11"

jfxSettings

JFX.mainClass := Some("org.edla.tmdb.shelf.Launcher")

JFX.nativeBundles := "all"

//required for Windows sbt package-javafx
//JFX.devKit := JFX.jdk("C:/Program Files/Java/jdk1.8.0_45")

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

resolvers += "edla" at "http://www.edla.org/releases"

resolvers += "ConJars" at "http://conjars.org/repo"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.12",
  "org.scala-lang.modules" %% "scala-async" % "0.9.5",
  "org.edla" %% "tmdb-async-client" % "0.8" exclude("io.spray", "spray-client"),
  "com.typesafe.slick" %% "slick" % "3.0.1",
  "com.h2database" % "h2" % "1.4.187",
  "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.13",
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

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

//pomIncludeRepository := { _ => false }

pomExtra := (
    <scm>
        <url>git@github.com:newca12/TMDb-shelf.git</url>
        <connection>scm:git:git@github.com:newca12/TMDb-shelf.git</connection>
    </scm>
    <developers>
        <developer>
            <id>newca12</id>
            <name>Olivier ROLAND</name>
            <url>http://www.edla.org</url>
        </developer>
    </developers>
    <contributors> </contributors>
    <properties>
        <encoding>UTF-8</encoding>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
	<build>
		<sourceDirectory>src/main/scala</sourceDirectory>
		<testSourceDirectory>src/test/scala</testSourceDirectory>
		<plugins>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-compiler-plugin</artifactId>
				<version>3.2</version>
				<configuration>
					<source>1.8</source>
					<target>1.8</target>
				</configuration>
			</plugin>
			<plugin>
				<groupId>net.alchim31.maven</groupId>
				<artifactId>scala-maven-plugin</artifactId>
				<version>3.2.0</version>
				<executions>
					<execution>
						<id>compile</id>
						<goals>
							<goal>compile</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>
	<reporting>
		<plugins>
			<plugin>
				<groupId>net.alchim31.maven</groupId>
				<artifactId>scala-maven-plugin</artifactId>
				<version>3.2.0</version>
			</plugin>
		</plugins>
	</reporting>
)
