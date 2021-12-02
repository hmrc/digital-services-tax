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

package unit.uk.gov.hmrc.digitalservicestax.services

import cats.syntax.option._
import com.github.fge.jackson.JsonLoader
import com.github.fge.jsonschema.core.report.ProcessingReport
import com.github.fge.jsonschema.main.JsonSchemaFactory
import play.api.libs.json._

case class SchemaChecker(schemaStream: java.io.InputStream) {
  val schemaText = scala.io.Source.fromInputStream(schemaStream).getLines.mkString
  schemaStream.close
  val schema = JsonLoader.fromString(schemaText)
  val validator = JsonSchemaFactory.byDefault.getValidator

  def errorsIn(json: JsValue): Option[ProcessingReport] = {
    val jsonIn = JsonLoader.fromString(Json.prettyPrint(json))
    val processingReport: ProcessingReport = validator.validate(schema, jsonIn)
    processingReport.some.filterNot(_.isSuccess)
  }
}

object SchemaChecker {

  private def sch(name: String): SchemaChecker =
    SchemaChecker(getClass.getResourceAsStream(name))

  def _1163 = GetReg
  object GetReg {
    lazy val request = sch("/dst/1163-get-reg.request.schema.json")
    lazy val response = sch("/dst/1163-get-reg.response.schema.json")
  }

  def _1166 = GetFinancialData
  object GetFinancialData {
    lazy val responseError = sch("/dst/1166-get-financial-data.response-error.schema.json")
    lazy val response = sch("/dst/1166-get-financial-data.response.schema.json")
  }

  def _1330 = GetObligation
  object GetObligation {
    lazy val responseError = sch("/dst/1330-get-obligation.response-error.schema.json")
    lazy val response = sch("/dst/1330-get-obligation.response.schema.json")
  }

  def _1335 = RegWithoutId
  object RegWithoutId {
    lazy val request = sch("/dst/1335-reg-without-id.request.schema.json")
    lazy val responseError = sch("/dst/1335-reg-without-id.response-error.schema.json")
    lazy val response = sch("/dst/1335-reg-without-id.response.schema.json")
  }

  def _1450 = PaymentTransactions
  object PaymentTransactions {
    lazy val request = sch("/dst/1450-payment-transactions.request.schema.json")
    lazy val responseError = sch("/dst/1450-payment-transactions.response-error.schema.json")
  }

  def _1479 = EeittSubscribe
  object EeittSubscribe {
    lazy val request = sch("/dst/1479-eeitt-subscribe.request.schema.json")
    lazy val responseError = sch("/dst/1479-eeitt-subscribe.response-error.schema.json")
    lazy val response = sch("/dst/1479-eeitt-subscribe.response.schema.json")
  }

  def _1480 = EeittReturn
  object EeittReturn {
    lazy val request = sch("/dst/1480-eeitt-return.request.schema.json")
    lazy val responseError = sch("/dst/1480-eeitt-return.response-error.schema.json")
    lazy val response = sch("/dst/1480-eeitt-return.response.schema.json")
  }
 }
