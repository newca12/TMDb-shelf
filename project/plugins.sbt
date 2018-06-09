//resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
addSbtPlugin("org.scalastyle"   %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("com.geirsson"     % "sbt-scalafmt"           % "1.6.0-RC2")
addSbtPlugin("org.scoverage"    % "sbt-scoverage"          % "1.5.1")
addSbtPlugin("com.eed3si9n"     % "sbt-buildinfo"          % "0.9.0")
addSbtPlugin("com.typesafe.sbt" %% "sbt-native-packager"   % "1.3.4")
addSbtPlugin("com.eed3si9n"     % "sbt-assembly"           % "0.14.6")
addSbtPlugin("ch.epfl.scala"    % "sbt-scalafix"           % "0.6.0-M3")
