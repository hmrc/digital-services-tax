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

package ltbs.resilientcalls

import java.time.LocalDateTime
import java.util.UUID
import shapeless.{:: => _,_}, tag._
import cats.implicits._

trait RetryRule[ERROR] {
  def nextRetry(previous: List[(LocalDateTime, ERROR)]): Option[LocalDateTime]
}

abstract class ResilientFunction[F[_], INPUT, OUTPUT, ERROR] {
  trait IDTag
  type ID = UUID @@ IDTag

  def async(input: INPUT): F[ID]

  protected def randomID(): ID = tag[IDTag][UUID](UUID.randomUUID)

  def get(id: ID): F[Either[List[(LocalDateTime, ERROR)],OUTPUT]]

  def map[O2](f: OUTPUT => O2)(implicit ev: cats.Functor[F]) = {
    val that = this
    new ResilientFunction[F, INPUT, O2, ERROR] {
      def async(input: INPUT): F[ID] =
        that.async(input).map{tag[IDTag][UUID](_)}
      def get(id: ID): F[Either[List[(LocalDateTime, ERROR)],O2]] =
        that.get(tag[that.IDTag][UUID](id)).map{
          _.map(f)
        }
    }
  }
}

abstract class ResilienceProvider[F[_]: cats.Monad, IT[_], OT[_], ERROR] {

  def apply[INPUT: IT, OUTPUT: OT](
    key: String,
    f: INPUT => F[OUTPUT],
    rule: RetryRule[ERROR]
  ): ResilientFunction[F, INPUT, OUTPUT, ERROR]
  
}

