package uk.gov.hmrc.digitalservicestax.connectors

import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.digitalservicestax.util.WiremockSpec

class TaxEnrolmentConnectorSpec extends WiremockSpec with ScalaCheckDrivenPropertyChecks {

  object TestConnector extends TaxEnrolmentConnector(httpClient, environment.mode, servicesConfig) {
    override val taxEnrolmentsUrl: String = mockServerUrl
  }

}