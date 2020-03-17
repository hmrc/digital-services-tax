package uk.gov.hmrc.digitalservicestax.connectors

import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.digitalservicestax.util.WiremockSpec

class ReturnConnectorSpec extends WiremockSpec with ScalaCheckDrivenPropertyChecks {

  object TestConnector extends ReturnConnector(httpClient, environment.mode, servicesConfig) {
    override val desURL: String = mockServerUrl
  }

}