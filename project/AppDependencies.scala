import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val compile = Seq(
    "com.github.fge"         %  "json-schema-validator"    % "2.2.6",
    "org.typelevel"          %% "cats-core"                % "2.0.0",
    // simple-reactivemongo held back because of platform issue
    "uk.gov.hmrc"            %% "simple-reactivemongo"     % "7.20.0-play-26",
    "org.reactivemongo"      %% "play2-reactivemongo"      % "0.18.6-play26",
    "uk.gov.hmrc"            %% "bootstrap-play-26"        % "1.5.0",
    "com.beachape"           %% "enumeratum"               % "1.5.13",
    "com.beachape"           %% "enumeratum-play-json"     % "1.5.13",
    "com.chuusai"            %% "shapeless"                % "2.3.3",
    "commons-validator"       % "commons-validator"        % "1.6"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-play-26"        % "1.5.0"                 % Test classifier "tests",
    "org.mockito"            % "mockito-core"              % "3.3.3"                 % Test,
    "org.scalatest"          %% "scalatest"                % "3.0.8"                 % Test,
    "com.typesafe.play"      %% "play-test"                % current                 % Test,
    "org.pegdown"             % "pegdown"                  % "1.6.0"                 % "test, it",
    "org.scalatestplus.play" %% "scalatestplus-play"       % "3.1.2"                 % "test, it", 
    "com.github.fge"          % "json-schema-validator"    % "2.2.6"                 % Test,
    "org.scalacheck"         %% "scalacheck"               % "1.14.1"                % Test,
    "io.chrisdavenport"      %% "cats-scalacheck"          % "0.2.0"                 % Test,
    "com.beachape"           %% "enumeratum-scalacheck"    % "1.5.16"                % Test,
    "wolfendale"             %% "scalacheck-gen-regexp"    % "0.1.2"                 % Test,
    "com.github.tomakehurst" %  "wiremock-jre8"            % "2.25.1"                % Test,
    "com.outworkers"         %% "util-samplers"            % "0.57.0"                % Test,
    "uk.gov.hmrc"            %% "reactivemongo-test"       % "4.19.0-play-26"        % Test

  )

}
