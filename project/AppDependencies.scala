import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  val hmrcMongoVersion = "2.12.0"
  val play             = "30"
  val bootstrapVersion = "10.7.0"

  val compile: Seq[ModuleID] = Seq(
    "com.github.java-json-tools" % "json-schema-validator"         % "2.2.14",
    "org.typelevel"             %% "cats-core"                     % "2.13.0",
    "uk.gov.hmrc.mongo"         %% s"hmrc-mongo-play-$play"        % hmrcMongoVersion,
    "uk.gov.hmrc"               %% s"bootstrap-backend-play-$play" % bootstrapVersion,
    "com.beachape"              %% "enumeratum"                    % "1.9.7",
    "com.beachape"              %% "enumeratum-play-json"          % "1.9.7",
    "commons-validator"          % "commons-validator"             % "1.10.1",
    "fr.marcwrobel"              % "jbanking"                      % "4.3.0"
  )

  val test: Seq[ModuleID] = Seq(
    "org.mockito"                   % "mockito-core"                % "5.23.0"         % Test,
    "org.scalatest"                %% "scalatest"                   % "3.2.20"         % Test,
    "org.scalatestplus.play"       %% "scalatestplus-play"          % "7.0.2"          % "test, it",
    "uk.gov.hmrc"                  %% s"bootstrap-test-play-$play"  % bootstrapVersion % Test,
    "org.scalatestplus"            %% "mockito-4-11"                % "3.2.18.0"       % Test,
    "org.scalatestplus"            %% "scalacheck-1-18"             % "3.2.19.0"       % Test,
    "org.scalacheck"               %% "scalacheck"                  % "1.19.0"         % Test,
    "io.chrisdavenport"            %% "cats-scalacheck"             % "0.3.2"          % Test,
    "com.beachape"                 %% "enumeratum-scalacheck"       % "1.9.7"          % Test,
    "io.github.wolfendale"         %% "scalacheck-gen-regexp"       % "1.1.0"          % Test,
    "com.vladsch.flexmark"          % "flexmark-all"                % "0.64.8"         % Test,
    "uk.gov.hmrc.mongo"            %% s"hmrc-mongo-test-play-$play" % hmrcMongoVersion % Test
  )

  // overrides the one in json-schema-validator
  val rhinoOverrides: Seq[ModuleID] = Seq("org.mozilla" % "rhino" % "1.9.1")

}
