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

import scala.concurrent.{Future,duration}, duration.FiniteDuration
import akka.actor._
import uk.gov.hmrc.digitalservicestax.config.AppConfig

trait AkkaScheduler {

  val actorSystem: ActorSystem
  def initialDelay: FiniteDuration
  def frequency: FiniteDuration  

  def tick(): Future[Unit]

  private val Tick = "tick"
  private class TickActor extends Actor {
    def receive = { case Tick => tick() }
  }

  private val tickActor =
    actorSystem.actorOf(Props(classOf[TickActor], this))

  import actorSystem.dispatcher
  val tickJob = actorSystem.scheduler.schedule(
    initialDelay,
    frequency,
    tickActor,
    Tick
  )

}
