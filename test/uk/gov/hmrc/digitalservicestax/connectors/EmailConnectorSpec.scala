package uk.gov.hmrc.digitalservicestax.connectors

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, stubFor, urlPathEqualTo}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.Json
import uk.gov.hmrc.digitalservicestax.backend_data.RosmRegisterWithoutIDRequest
import uk.gov.hmrc.digitalservicestax.data.{Company, ContactDetails, DSTRegNumber, NonEmptyString, Period}
import uk.gov.hmrc.digitalservicestax.util.WiremockSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.digitalservicestax.util.TestInstances._
import org.scalacheck.Arbitrary.arbitrary

class EmailConnectorSpec extends WiremockSpec with ScalaCheckDrivenPropertyChecks {

  object TestConnector extends EmailConnector(httpClient, environment.mode, servicesConfig) {
    override val emailUrl: String = mockServerUrl
  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "should get no response back if des is not available" in {
    val contactDetails = arbitrary[ContactDetails].sample.value
    val parentRef = arbitrary[NonEmptyString].sample.value
    val dstNumber = arbitrary[DSTRegNumber].sample.value
    val period = arbitrary[Period].sample.value

    stubFor(
      post(urlPathEqualTo("/hmrc/email"))
        .willReturn(aResponse()
        .withStatus(200)))

    val response = TestConnector.sendConfirmationEmail(contactDetails, parentRef, dstNumber, period)
    whenReady(response) { res => }

  }

  "should get an upstream5xx response if des is returning 429" in {}

}
