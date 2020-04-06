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

import akka.actor.ActorSystem
import javax.inject._
import play.modules.reactivemongo._
import scala.concurrent._
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.http._

@Singleton
class DstMongoProvider @Inject()(
  mongo: ReactiveMongoApi,
  val actorSystem: ActorSystem,
  val appConfig: AppConfig,
  implicit val ec: ExecutionContext  
) extends MongoProvider[(Int, String)](mongo,
  {
    case Upstream4xxResponse(msg, code, _, _) => (code, msg)
    case Upstream5xxResponse(msg, code, _) => (code, msg)
    case e => (-1, e.getLocalizedMessage)
  }
) with AkkaScheduler {
  override val maxTasks: Int = appConfig.resilience.maxTasks
  def initialDelay = appConfig.resilience.tickDelay
  def frequency = appConfig.resilience.tickFrequency
}
