import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val hmrcMongoVersion = "0.59.0"
  val play = "28"

  val compile = Seq(
    "com.github.java-json-tools"     %  "json-schema-validator"         % "2.2.14",
    "org.typelevel"                  %% "cats-core"                     % "2.7.0",
    "uk.gov.hmrc"                    %% "simple-reactivemongo"          % s"8.0.0-play-$play",
    "org.reactivemongo"              %% "play2-reactivemongo"           % s"0.18.6-play26", // problem uprgading
    "uk.gov.hmrc.mongo"              %% s"hmrc-mongo-play-$play"        % hmrcMongoVersion,
    "uk.gov.hmrc"                    %% s"bootstrap-backend-play-$play" % "5.20.0",
    "com.beachape"                   %% "enumeratum"                    % "1.7.0",
    "com.beachape"                   %% "enumeratum-play-json"          % "1.7.0",
    "com.chuusai"                    %% "shapeless"                     % "2.3.7",
    "commons-validator"              % "commons-validator"              % "1.6",
    "fr.marcwrobel"                  % "jbanking"                       % "3.1.1",
    compilerPlugin("com.github.ghik" %% "silencer-plugin"               % "1.4.2"),
    "com.github.ghik"                %% "silencer-lib"                  % "1.4.2" % Provided
  )

  val test = Seq(
    "org.mockito"            % "mockito-core"                 % "4.3.1"                 % Test,
    "org.scalatest"          %% "scalatest"                   % "3.0.8"                 % Test, // problem upgrading to 3.2.10
    "com.typesafe.play"      %% "play-test"                   % current                 % Test,
    "org.pegdown"            %  "pegdown"                     % "1.6.0"                 % "test, it",
    "org.scalatestplus.play" %% "scalatestplus-play"          % "5.1.0"                 % "test, it",
    "org.scalatestplus"      %% "mockito-3-12"                % "3.2.10.0"              % Test,
    "org.scalatestplus"      %% "scalacheck-1-15"             % "3.2.10.0"              % Test,
    "org.scalacheck"         %% "scalacheck"                  % "1.15.4"                % Test,
    "io.chrisdavenport"      %% "cats-scalacheck"             % "0.3.1"                 % Test,
    "com.beachape"           %% "enumeratum-scalacheck"       % "1.7.0"                 % Test,
    "wolfendale"             %% "scalacheck-gen-regexp"       % "0.1.2"                 % Test, // problem upgrading to 0.1.3
    "com.github.tomakehurst" %  "wiremock-jre8"               % "2.27.2"                % Test,
    "com.outworkers"         %% "util-samplers"               % "0.57.0"                % Test,
    "uk.gov.hmrc"            %% "reactivemongo-test"          % s"5.0.0-play-$play"     % Test,
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-test-play-$play" % hmrcMongoVersion        % Test
  )

}
