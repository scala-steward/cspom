import sbt._

object plugins extends Build {
  lazy val plugins = Project("plugins", file("."))
    .dependsOn(
      uri("https://github.com/bseibel/sbt-simple-junit-xml-reporter-plugin.git"))
}
