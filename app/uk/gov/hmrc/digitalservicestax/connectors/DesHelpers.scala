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

package uk.gov.hmrc.digitalservicestax.connectors

import play.api.libs.json.Writes
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.data.RichLocalDateTime
import scala.concurrent.{ExecutionContext, Future}

trait DesHelpers {

  def http: HttpClient
  def servicesConfig: ServicesConfig
  def desGet[O](url: String)(implicit rds: HttpReads[O], hc: HeaderCarrier, ec: ExecutionContext): Future[O] =
    http.GET[O](url)(rds, addHeaders, ec)

  def desPost[I, O](url: String, body: I)(implicit wts: Writes[I], rds: HttpReads[O], hc: HeaderCarrier, ec: ExecutionContext): Future[O] =
    http.POST[I, O](url, body)(wts, rds, addHeaders, ec)

  def desPut[I, O](url: String, body: I)(implicit wts: Writes[I], rds: HttpReads[O], hc: HeaderCarrier, ec: ExecutionContext): Future[O] =
    http.PUT[I, O](url, body)(wts, rds, addHeaders, ec)

  def addHeaders(implicit hc: HeaderCarrier): HeaderCarrier = {
    hc.withExtraHeaders(
      "Environment" -> servicesConfig.getConfString("des.environment", "")
    ).copy(authorization = Some(Authorization(s"Bearer ${servicesConfig.getConfString("des.token", "")}")))
  }

}

case class DesRetryRule(config: AppConfig) extends ltbs.resilientcalls.RetryRule[(Int,String)] {

  import concurrent._, duration._
  import java.time.LocalDateTime

  def nextRetry(previous: List[(LocalDateTime, (Int,String))]): Option[LocalDateTime] = {

    // retry 5XX errors, give up on everything else
    def isFatal(t: (Int,String)): Boolean =
      t._1 < 500 || t._1 >= 600

    // double the previous delay after each failed attempt
    // give up after 5 failed attempts (or a fatal error)
    previous match {
      case ((_,lastError)::_) if isFatal(lastError) =>
        // give up if the last attempt was a fatal error
        None
      case xs if xs.size >= config.resilience.maxAttempts =>
        // give up after 5 attempts
        None
      case Nil =>
        // if this is the first attempt, wait until DES is live
        Some(config.resilience.desEnabledOn)
      case ((firstAttempt,_)::Nil) =>
        Some(firstAttempt + config.resilience.initialDelay)
      case ((lastAttempt,_)::(penultimateAttempt,_)::_) =>
        // calculate the delay between the last two attempts and increase it by the rampup factor
        (lastAttempt - penultimateAttempt) * config.resilience.rampUp match {
          case f: FiniteDuration => Some(lastAttempt + f)
          case _ => None
        }
    }
  }
}

