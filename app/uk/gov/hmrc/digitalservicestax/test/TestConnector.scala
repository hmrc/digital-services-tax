/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.digitalservicestax.test

import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.digitalservicestax.connectors.{Identifier, TaxEnrolmentsSubscription}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestConnector @Inject()(
  http: HttpClient,
  environment: Environment,
  configuration: Configuration,
  servicesConfig: ServicesConfig
) {

  private val stubUrl: String = servicesConfig.baseUrl("des")

  def trigger(url: String, param: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.GET[HttpResponse](s"$stubUrl/$url/$param")

  def getSubscription(subscriptionId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TaxEnrolmentsSubscription] =
    http.GET[DstRegNoWrapper](s"$stubUrl/get-subscription/$subscriptionId").map { x =>
      TaxEnrolmentsSubscription(Some(List(Identifier("DstRefNo", x.dstRegNo))), "FOOBAR", "FOOBAR", None)
    }

}

