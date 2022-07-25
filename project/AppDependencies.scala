import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val hmrcMongoVersion = "0.68.0"
  val play = "28"

  val compile = Seq(
    "com.github.java-json-tools"     %  "json-schema-validator"         % "2.2.14",
    "org.typelevel"                  %% "cats-core"                     % "2.7.0",
    "uk.gov.hmrc.mongo"              %% s"hmrc-mongo-play-$play"        % hmrcMongoVersion,
    "uk.gov.hmrc"                    %% s"bootstrap-backend-play-$play" % "5.24.0",
    "com.beachape"                   %% "enumeratum"                    % "1.7.0",
    "com.beachape"                   %% "enumeratum-play-json"          % "1.7.0",
    "com.chuusai"                    %% "shapeless"                     % "2.3.7",
    "commons-validator"              % "commons-validator"              % "1.6",
    "fr.marcwrobel"                  % "jbanking"                       % "3.1.1",
    compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.9" cross CrossVersion.full),
    "com.github.ghik" % "silencer-lib" % "1.7.9" % Provided cross CrossVersion.full
  )

  val test = Seq(
    "org.mockito"            % "mockito-core"                 % "4.3.1"                 % Test,
    "org.scalatest"          %% "scalatest"                   % "3.2.10"                % Test,
    "com.typesafe.play"      %% "play-test"                   % current                 % Test,
    "org.pegdown"            %  "pegdown"                     % "1.6.0"                 % "test, it",
    "org.scalatestplus.play" %% "scalatestplus-play"          % "5.1.0"                 % "test, it",
    "org.scalatestplus"      %% "mockito-3-12"                % "3.2.10.0"              % Test,
    "org.scalatestplus"      %% "scalacheck-1-15"             % "3.2.10.0"              % Test,
    "org.scalacheck"         %% "scalacheck"                  % "1.15.4"                % Test,
    "io.chrisdavenport"      %% "cats-scalacheck"             % "0.3.1"                 % Test,
    "com.beachape"           %% "enumeratum-scalacheck"       % "1.7.0"                 % Test,
    "wolfendale"             %% "scalacheck-gen-regexp"       % "0.1.2"                 % Test,
    "com.vladsch.flexmark"   %   "flexmark-all"               % "0.62.2"                % Test,
    "com.github.tomakehurst" %  "wiremock-jre8"               % "2.27.2"                % Test,
    "com.outworkers"         %% "util-samplers"               % "0.57.0"                % Test,
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-test-play-$play" % hmrcMongoVersion        % Test

  )

}
