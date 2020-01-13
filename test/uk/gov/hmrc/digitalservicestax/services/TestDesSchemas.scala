/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.digitalservicestax.services

import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.json._
class TestDesSchemas extends FlatSpec with Matchers {

  val dir = new java.io.File("conf/dst")
  dir.listFiles().filter{_.getName.contains("example")}.sortBy(_.getName) map { file =>
    val schemaName = file.getAbsolutePath().replaceAll("example[0-9]","schema")
    val schemaFile = new java.io.File(schemaName)
    s"${file.getName}" should s"have a schema called ${schemaFile.getName}" in {
      schemaFile.exists shouldBe (true)
    }

    it should s"conform to its schema" in {
      val is = new java.io.FileInputStream(schemaFile)
      val checker = SchemaChecker(is)
      val example = scala.io.Source.fromFile(file).getLines().mkString
      val result = checker.errorsIn(Json.parse(example))
      is.close
      result shouldBe (None)
    } 
  }
}
