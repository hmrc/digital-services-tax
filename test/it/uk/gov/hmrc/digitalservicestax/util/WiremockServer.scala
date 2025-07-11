/*
 * Copyright 2023 HM Revenue & Customs
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

package it.uk.gov.hmrc.digitalservicestax.util

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}

trait WiremockServer extends BeforeAndAfterAll with BeforeAndAfterEach {
  this: Suite =>

  val wiremockPort: Int                  = WireMockSupport.port
  protected[this] val mockServer = new WireMockServer(wiremockPort)

  val mockServerUrl = s"http://localhost:$wiremockPort"

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    mockServer.start()
    WireMock.configureFor("localhost", WireMockSupport.port)
  }

  protected val baseUrl = s"http://localhost:${WireMockSupport.port}"

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    WireMock.reset()
  }

  object WireMockSupport {
    val port = 11111
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    mockServer.stop()
  }
}
