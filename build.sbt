val akkaHttpVersion        = "10.2.6"
val akkaVersion            = "2.6.15"
val zioVersion             = "1.0.10"
val zioLoggingVersion      = "0.5.11"
val zioConfigVersion       = "1.0.6"
val zioRSVersion           = "1.3.5"
val logbackClassicVersion  = "1.2.5"
val zioAkkaHttpInterop     = "0.5.0"
val zioJsonVersion         = "0.1.5"
val akkaHttpZioJson        = "1.37.0"
val proxVersion            = "0.7.3"
val catsCollectionsVersion = "0.9.0"
val dockerReleaseSettings = Seq(
  dockerExposedPorts := Seq(8080),
  dockerExposedVolumes := Seq("/opt/docker/logs"),
  dockerBaseImage := "adoptopenjdk/openjdk12:x86_64-ubuntu-jre-12.0.2_10"
)

lazy val It = config("it").extend(Test)

val root = (project in file("."))
  .configs(It)
  .settings(
    inConfig(It)(Defaults.testSettings),
    inThisBuild(
      List(
        organization := "io.github.petoalbert",
        scalaVersion := "2.13.6"
      )
    ),
    name := "wordcount",
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka"     %% "akka-http"                       % akkaHttpVersion,
      "com.typesafe.akka"     %% "akka-actor-typed"                % akkaVersion,
      "com.typesafe.akka"     %% "akka-stream"                     % akkaVersion,
      "dev.zio"               %% "zio-json"                        % zioJsonVersion,
      "de.heikoseeberger"     %% "akka-http-zio-json"              % akkaHttpZioJson,
      "dev.zio"               %% "zio"                             % zioVersion,
      "dev.zio"               %% "zio-streams"                     % zioVersion,
      "dev.zio"               %% "zio-config"                      % zioConfigVersion,
      "dev.zio"               %% "zio-config-magnolia"             % zioConfigVersion,
      "dev.zio"               %% "zio-config-typesafe"             % zioConfigVersion,
      "io.scalac"             %% "zio-akka-http-interop"           % zioAkkaHttpInterop,
      "dev.zio"               %% "zio-interop-reactivestreams"     % zioRSVersion,
      "ch.qos.logback"        % "logback-classic"                  % logbackClassicVersion,
      "dev.zio"               %% "zio-logging"                     % zioLoggingVersion,
      "dev.zio"               %% "zio-logging-slf4j"               % zioLoggingVersion,
      "io.github.vigoo"       %% "prox-zstream"                    % proxVersion,
      "org.typelevel"         %% "cats-collections-core"           % catsCollectionsVersion,
      "com.typesafe.akka"     %% "akka-http-testkit"               % akkaHttpVersion % Test,
      "com.typesafe.akka"     %% "akka-stream-testkit"             % akkaVersion % Test,
      "com.typesafe.akka"     %% "akka-actor-testkit-typed"        % akkaVersion % Test,
      "dev.zio"               %% "zio-test-sbt"                    % zioVersion % Test,
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
    dockerReleaseSettings
  )
  .enablePlugins(DockerPlugin, JavaAppPackaging)
