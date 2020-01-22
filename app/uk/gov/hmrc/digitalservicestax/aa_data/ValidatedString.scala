/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.digitalservicestax.data

import shapeless._, tag._
import cats.implicits._

trait ValidatedString[Tag] {

  lazy val className = this.getClass.getName

  def validateAndTransform(in: String): Option[String]

  def apply(in: String): String @@ Tag =
    of(in).getOrElse{
      throw new IllegalArgumentException(
        s""""$in" is not a valid ${className.init}"""
      )
    }

  def of(in: String): Option[String @@ Tag] =
    validateAndTransform(in) map {
      x => tag[Tag][String](x)
    }
}

class RegexValidatedString[Tag](
  regex: String,
  transform: String => String = identity
) extends ValidatedString[Tag] {

  val regexCompiled = regex.r

  def validateAndTransform(in: String): Option[String] =
    transform(in).some.filter(regexCompiled.findFirstIn(_).isDefined)
}



