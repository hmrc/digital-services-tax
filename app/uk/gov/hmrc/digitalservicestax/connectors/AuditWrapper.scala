package uk.gov.hmrc.digitalservicestax
package controllers

import scala.concurrent._
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

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
