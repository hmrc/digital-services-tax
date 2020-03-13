import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val compile = Seq(
    "com.github.fge"         %  "json-schema-validator"    % "2.2.6",
    "org.typelevel"          %% "cats-core"                % "2.0.0",
    "uk.gov.hmrc"            %% "simple-reactivemongo"     % "7.24.0-play-26",
    "org.reactivemongo"      %% "play2-reactivemongo"      % "0.18.6-play26",    
    "uk.gov.hmrc"            %% "bootstrap-play-26"        % "1.5.0",
    "com.beachape"           %% "enumeratum"               % "1.5.13",
    "com.beachape"           %% "enumeratum-play-json"     % "1.5.13",
    "com.chuusai"            %% "shapeless"                % "2.3.3",
    "commons-validator"       % "commons-validator"        % "1.6"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-play-26"        % "1.5.0"                 % Test classifier "tests",
    "org.scalatest"          %% "scalatest"                % "3.0.8"                 % "test",
    "com.typesafe.play"      %% "play-test"                % current                 % "test",
    "org.pegdown"             % "pegdown"                  % "1.6.0"                 % "test, it",
    "org.scalatestplus.play" %% "scalatestplus-play"       % "3.1.2"                 % "test, it", 
    "com.github.fge"          % "json-schema-validator"    % "2.2.6"                 % "test",
    "org.scalacheck"         %% "scalacheck"               % "1.14.1"                % "test",
    "io.chrisdavenport"      %% "cats-scalacheck"          % "0.2.0"                 % "test",
    "com.beachape"           %% "enumeratum-scalacheck"    % "1.5.16"                % "test",
    "wolfendale"             %% "scalacheck-gen-regexp"    % "0.1.2"                 % "test"
    
  )

}
