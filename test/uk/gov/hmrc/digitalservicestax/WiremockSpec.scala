package uk.gov.hmrc.digitalservicestax

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import org.scalatest._
import org.scalatest.concurrent.{Eventually, ScalaFutures}

import scala.concurrent.ExecutionContext

trait WiremockSpec extends FakeApplicationSpec with BeforeAndAfterEach with BeforeAndAfterAll with ScalaFutures {
  val port = WireMockSupport.port
  val mockServer = new WireMockServer(port)

  implicit lazy val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val mockServerUrl = s"http://localhost:$port"

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
