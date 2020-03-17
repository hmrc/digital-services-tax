package uk.gov.hmrc.digitalservicestax.connectors

import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.digitalservicestax.util.WiremockSpec

class RegistrationConnectorSpec extends WiremockSpec with ScalaCheckDrivenPropertyChecks {

  object TestConnector extends RegistrationConnector(httpClient, environment.mode, servicesConfig) {
    override val desURL: String = mockServerUrl
  }

}