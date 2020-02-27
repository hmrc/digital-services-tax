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

import shapeless._
import tag._
import cats.implicits._
import scala.util.matching.Regex
import play.api.mvc.PathBindable

trait ValidatedType[BaseType] {

  trait Tag

  lazy val className: String = this.getClass.getSimpleName

  def validateAndTransform(in: BaseType): Option[BaseType]

  def apply(in: BaseType): BaseType @@ Tag =
    of(in).getOrElse{
      throw new IllegalArgumentException(
        s""""$in" is not a valid ${className.init}"""
      )
    }

  def of(in: BaseType): Option[BaseType @@ Tag] =
    validateAndTransform(in) map {
      x => tag[Tag][BaseType](x)
    }

  def mapTC[TC[_]: cats.Functor](implicit monA: TC[BaseType]): TC[BaseType @@ Tag] =
    monA.map(apply)

  implicit def pathBinder(implicit baseBinder: PathBindable[BaseType]) =
    new PathBindable[BaseType @@ Tag] {
      import cats.syntax.either._

      override def bind(key: String, value: String): Either[String, BaseType @@ Tag] =
        baseBinder.bind(key, value) flatMap {of(_) match {
          case Some(x) => Right(x: BaseType @@ Tag)
          case None    => Left(s""""$value" is not a valid ${className.init}""")
        }}

      override def unbind(key: String, value: BaseType @@ Tag): String =
        baseBinder.unbind(key, value)
    }
}

class RegexValidatedString(
  val regex: String,
  transform: String => String = identity
) extends ValidatedType[String] {

  val regexCompiled: Regex = regex.r

  def validateAndTransform(in: String): Option[String] =
    transform(in).some.filter(regexCompiled.findFirstIn(_).isDefined)
}



