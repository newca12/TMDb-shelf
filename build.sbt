name := "TMDb-shelf"

organization := "org.edla"

version := "0.1"

jfxSettings

JFX.mainClass := Some("org.edla.tmdb.shelf.Launcher")

JFX.nativeBundles := "msi"

scalaVersion := "2.11.2"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-optimize")

scalacOptions in (Compile, doc) ++= Seq("-diagrams","-implicits")

org.scalastyle.sbt.ScalastylePlugin.Settings

resolvers += "edla" at "http://www.edla.org/snapshots"

resolvers += "ConJars" at "http://conjars.org/repo"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.6",
  "io.spray" %% "spray-client" % "1.3.1",
  "io.spray" %%  "spray-json" % "1.2.6",
  "org.scala-lang.modules" %% "scala-async" % "0.9.2",
  "com.pragmasoft" %% "spray-funnel" % "1.0-RC4-spray1.3" intransitive,  
  "org.edla" %% "tmdb-async-client" % "0.5-SNAPSHOT" intransitive,
  "org.controlsfx" % "controlsfx" % "8.0.6_20",
  "com.typesafe.slick" %% "slick" % "2.1.0",
  "com.h2database" % "h2" % "1.4.181",
  "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.9",
  "org.apache.commons" % "commons-lang3" % "3.3.2",  
  "com.github.tototoshi" %% "scala-csv" % "1.0.0",
  "junit" % "junit" % "4.11" % "test",
  "org.specs2" %% "specs2" % "2.3.13" % "test",
  "org.scalatest" %% "scalatest" % "2.2.0" % "test"
)

fork := true

seq(CoverallsPlugin.singleProject: _*)

licenses := Seq("GNU GPL v3" -> url("http://www.gnu.org/licenses/gpl.html"))

homepage := Some(url("http://github.com/newca12/TMDb-shelf"))

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
				<version>3.1</version>
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
							<goal>testCompile</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-surefire-plugin</artifactId>
				<version>2.16</version>
				<configuration>
					<includes>
						<include>**/*Suite.class</include>
						<include>**/*Test.class</include>
						<include>**/*Tests.class</include>
						<include>**/*Spec.class</include>
						<include>**/*Specs.class</include>
					</includes>
				</configuration>
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
