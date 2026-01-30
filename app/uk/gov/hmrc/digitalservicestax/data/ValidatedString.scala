/*
 * Copyright 2026 HM Revenue & Customs
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

import cats.implicits._

import scala.util.matching.Regex

trait ValidatedType[BaseType] {

  lazy val className: String = this.getClass.getSimpleName

  def validateAndTransform(in: BaseType): Option[BaseType]

  // Subclasses should define: opaque type Validated = BaseType
  // and implement: def tag(value: BaseType): Validated = value
  
  type Validated
  
  protected def tag(value: BaseType): Validated

  def apply(in: BaseType): Validated =
    of(in).getOrElse {
      throw new IllegalArgumentException(
        s""""$in" is not a valid ${className.init}"""
      )
    }

  def of(in: BaseType): Option[Validated] =
    validateAndTransform(in).map(tag)

  extension(v: Validated) {
    def value: BaseType = v.asInstanceOf[BaseType]
  }
}

abstract class RegexValidatedString(
  val regex: String,
  transform: String => String = identity
) extends ValidatedType[String] {

  val regexCompiled: Regex = regex.r

  def validateAndTransform(in: String): Option[String] =
    transform(in).some.filter(regexCompiled.findFirstIn(_).isDefined)
}
