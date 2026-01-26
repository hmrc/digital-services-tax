import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  val hmrcMongoVersion = "2.12.0"
  val play             = "30"
  val bootstrapVersion = "10.5.0"

  val compile = Seq(
    "com.github.java-json-tools" % "json-schema-validator"         % "2.2.14",
    "org.typelevel"             %% "cats-core"                     % "2.10.0",
    "uk.gov.hmrc.mongo"         %% s"hmrc-mongo-play-$play"        % hmrcMongoVersion,
    "uk.gov.hmrc"               %% s"bootstrap-backend-play-$play" % bootstrapVersion,
    "com.beachape"              %% "enumeratum"                    % "1.7.3",
    "com.beachape"              %% "enumeratum-play-json"          % "1.8.0",
    "com.chuusai"               %% "shapeless"                     % "2.4.0-M1",
    "commons-validator"          % "commons-validator"             % "1.7",
    "fr.marcwrobel"              % "jbanking"                      % "4.2.0"
  )

  val test = Seq(
    "org.mockito"                   % "mockito-core"                % "5.11.0"          % Test,
    "org.scalatest"                %% "scalatest"                   % "3.2.18"         % Test,
    "org.scalatestplus.play"       %% "scalatestplus-play"          % "7.0.1"          % "test, it",
    "uk.gov.hmrc"                  %% s"bootstrap-test-play-$play"  % bootstrapVersion         % Test,
    "org.scalatestplus"            %% "mockito-4-11"                % "3.2.17.0"       % Test,
    "org.scalatestplus"            %% "scalacheck-1-15"             % "3.2.11.0"       % Test,
    "org.scalacheck"               %% "scalacheck"                  % "1.18.0"         % Test,
    "io.chrisdavenport"            %% "cats-scalacheck"             % "0.3.2"          % Test,
    "com.beachape"                 %% "enumeratum-scalacheck"       % "1.7.3"          % Test,
    "wolfendale"                   %% "scalacheck-gen-regexp"       % "0.1.2"          % Test,
    "com.vladsch.flexmark"          % "flexmark-all"                % "0.64.8"         % Test,
    "com.outworkers"               %% "util-samplers"               % "0.57.0"         % Test,
    "uk.gov.hmrc.mongo"            %% s"hmrc-mongo-test-play-$play" % hmrcMongoVersion % Test
  )

}
