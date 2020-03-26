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

package uk.gov.hmrc.digitalservicestax
package connectors

import javax.inject.{Inject, Singleton}
import play.api.{Logger, Mode}
import play.api.libs.json._
import uk.gov.hmrc.digitalservicestax.backend_data.{RegistrationResponse, RosmWithoutIDResponse}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.data.{Registration, SafeId, BackendAndFrontendJson}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import ltbs.resilientcalls._
import java.time.LocalDateTime

@Singleton
class RegistrationConnector @Inject()(
  val http: HttpClient,
  val mode: Mode,
  servicesConfig: ServicesConfig,
  appConfig: AppConfig,
  ec: ExecutionContext,
  resilienceProvider: DstMongoProvider
)
  extends DesHelpers(servicesConfig) {

  val desURL: String = servicesConfig.baseUrl("des")
  val registerPath = "cross-regime/subscription/DST"

  val resilientSend: ResilientFunction[Future, (String, Option[String], Registration), Option[RegistrationResponse], (Int,String)] = {
    def rule = new RetryRule[(Int,String)] {

      def nextRetry(previous: List[(LocalDateTime, (Int,String))]): Option[LocalDateTime] = {

        def isFatal(t: (Int,String)): Boolean = false

        previous match {
          case ((_,lastError)::_) if isFatal(lastError) => None
          case xs if xs.size > 4 => None
          case r =>
            val delay: Duration = ((Math.pow(2,r.size)) * 5.minute)
            Some(LocalDateTime.now.plusSeconds(delay.toSeconds))

        }
      }
    }

    val bareFunction: ((String, Option[String], Registration)) => Future[Option[RegistrationResponse]] = {
       case (idType: String, idNumber: Option[String], request: Registration) =>
         implicit val hc = new HeaderCarrier // will this work with the HoD?
         implicit val e = ec
         send(idType, idNumber, request)(addHeaders, ec) // either way you'll need the added headers
     }

    import BackendAndFrontendJson._

    implicit def optFormat[A](implicit in: Format[A]) = new Format[Option[A]] {
      def reads(json: JsValue): JsResult[Option[A]] = json match {
        case JsNull => JsSuccess(None)
        case x => in.reads(x).map{Some(_)}
      }
      def writes(o: Option[A]): JsValue = o.fold(JsNull: JsValue)(in.writes)
    }

    resilienceProvider.apply("send-registration", bareFunction, rule)
  }

  def send(
    idType: String,
    idNumber: Option[String],
    request: Registration
  )(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[RegistrationResponse]] = {

    import services.EeittInterface.registrationWriter

    (idType, idNumber) match {
      case (t, Some(i)) => {
        val result = desPost[JsValue, Option[RegistrationResponse]](
          s"$desURL/$registerPath/$t/$i", Json.toJson(request)
        )(implicitly, implicitly, addHeaders, implicitly)

        if (appConfig.logRegResponse) Logger.debug(
          s"Registration response is ${Await.result(result, 20.seconds)}"
        )
        result
      }

      case _ =>
        Future.failed(new IllegalArgumentException(s"Missing idNumber for idType: $idType"))
    }
  }
}
