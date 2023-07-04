import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val hmrcMongoVersion = "0.73.0"
  val play             = "28"

  val compile = Seq(
    "com.github.java-json-tools" % "json-schema-validator"         % "2.2.14",
    "org.typelevel"             %% "cats-core"                     % "2.8.0",
    "uk.gov.hmrc.mongo"         %% s"hmrc-mongo-play-$play"        % hmrcMongoVersion,
    "uk.gov.hmrc"               %% s"bootstrap-backend-play-$play" % "7.11.0",
    "com.beachape"              %% "enumeratum"                    % "1.7.0",
    "com.beachape"              %% "enumeratum-play-json"          % "1.7.0",
    "com.chuusai"               %% "shapeless"                     % "2.4.0-M1",
    "commons-validator"          % "commons-validator"             % "1.7",
    "fr.marcwrobel"              % "jbanking"                      % "3.4.0"
  )

  val test = Seq(
    "org.mockito"                   % "mockito-core"                  % "4.8.0"          % Test,
    "org.scalatest"                %% "scalatest"                     % "3.2.14"         % Test,
    "com.typesafe.play"            %% "play-test"                     % current          % Test,
    "org.pegdown"                   % "pegdown"                       % "1.6.0"          % "test, it",
    "org.scalatestplus.play"       %% "scalatestplus-play"            % "5.1.0"          % "test, it",
    "uk.gov.hmrc"                  %% s"bootstrap-test-play-$play"    % "7.11.0"         % Test,
    "org.scalatestplus"            %% "mockito-3-12"                  % "3.2.10.0"       % Test,
    "org.scalatestplus"            %% "scalacheck-1-15"               % "3.2.11.0"       % Test,
    "org.scalacheck"               %% "scalacheck"                    % "1.17.0"         % Test,
    "io.chrisdavenport"            %% "cats-scalacheck"               % "0.3.2"          % Test,
    "com.beachape"                 %% "enumeratum-scalacheck"         % "1.7.0"          % Test,
    "wolfendale"                   %% "scalacheck-gen-regexp"         % "0.1.2"          % Test,
    "com.vladsch.flexmark"          % "flexmark-all"                  % "0.62.2"         % Test,
    "com.github.tomakehurst"        % "wiremock-standalone"           % "2.27.2"         % Test,
    "com.outworkers"               %% "util-samplers"                 % "0.57.0"         % Test,
    "uk.gov.hmrc.mongo"            %% s"hmrc-mongo-test-play-$play"   % hmrcMongoVersion % Test,
    "com.fasterxml.jackson.module" %% "jackson-module-scala"          % "2.13.4"         % Test
  )

}
