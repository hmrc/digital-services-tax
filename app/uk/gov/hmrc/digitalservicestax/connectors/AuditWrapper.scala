/*
 * Copyright 2022 HM Revenue & Customs
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
package controllers

import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import scala.concurrent._

trait AuditWrapper {
  def auditing: AuditConnector

  implicit class RichFuture[A](in: Future[A]) {
    def auditError(error: Throwable => ExtendedDataEvent)(implicit ec: ExecutionContext): Future[A] =
      in.recoverWith { case e =>
        auditing.sendExtendedEvent(
          error(e)
        ) map {
          throw e
        }
      }

    def auditSuccess(event: A => ExtendedDataEvent)(implicit ec: ExecutionContext): Future[A] =
      in.flatMap { e =>
        auditing.sendExtendedEvent(event(e)).map(_ => e)
      }
  }
}
