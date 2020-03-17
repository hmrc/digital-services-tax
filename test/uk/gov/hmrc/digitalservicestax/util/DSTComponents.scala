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

package uk.gov.hmrc.digitalservicestax.util

class DSTComponents {

}


import com.softwaremill.macwire._
import play.api.ApplicationLoader.Context
import play.api.http.{HttpErrorHandler, HttpRequestHandler}
import play.api.i18n.I18nComponents
import play.api.inject.{Injector, SimpleInjector}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.ControllerComponents
import play.api.{Application, BuiltInComponentsFromContext, Configuration, DefaultApplication}
import play.filters.HttpFiltersComponents
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.bootstrap.config.Base64ConfigDecoder
import uk.gov.hmrc.play.bootstrap.http._

import scala.concurrent.ExecutionContext
