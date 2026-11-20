/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.onlineagentauthorisations.connectors

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.*
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.onlineagentauthorisations.models.AgentListCounts

class OaaExbConnectorSpec extends AnyFreeSpec with Matchers with ScalaFutures with IntegrationPatience with WireMockSupport {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private def app(stubEnabled: Boolean): Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.online-agent-authorisations-stub.port" -> wireMockPort,
        "microservice.services.oaa-exb.port"                          -> wireMockPort,
        "features.stub-oaa-exb-enabled"                               -> stubEnabled,
        "http-verbs.retries.enabled"                                  -> false
      )
      .build()

  private lazy val connectorWithStub: OaaExbConnector   = app(stubEnabled = true).injector.instanceOf[OaaExbConnector]
  private lazy val connectorWithOaaExb: OaaExbConnector = app(stubEnabled = false).injector.instanceOf[OaaExbConnector]

  private val stubUrl   = "/online-agent-authorisations-stub/count-agent-lists"
  private val oaaExbUrl = "/oaa-exb/count-agent-lists"

  "OaaExbConnector.countAgentLists" - {

    "must call the stub when stub-oaa-exb-enabled is true" in {
      wireMockServer.stubFor(
        post(urlEqualTo(stubUrl))
          .withRequestBody(equalToJson("""{ "agentCode": "AGENTCODE1" }"""))
          .willReturn(okJson("""{ "unsubmitted": 2, "submitted": 3 }"""))
      )

      connectorWithStub.stubOaaExbEnabled mustBe true
      connectorWithStub.countAgentLists("AGENTCODE1").futureValue mustBe AgentListCounts(2, 3)
    }

    "must call oaa-exb when stub-oaa-exb-enabled is false" in {
      wireMockServer.stubFor(
        post(urlEqualTo(oaaExbUrl))
          .withRequestBody(equalToJson("""{ "agentCode": "AGENTCODE1" }"""))
          .willReturn(okJson("""{ "unsubmitted": 0, "submitted": 0 }"""))
      )

      connectorWithOaaExb.stubOaaExbEnabled mustBe false
      connectorWithOaaExb.countAgentLists("AGENTCODE1").futureValue mustBe AgentListCounts(0, 0)
    }

    "must fail with an UpstreamErrorResponse when the downstream returns a 500" in {
      wireMockServer.stubFor(post(urlEqualTo(stubUrl)).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

      val error = connectorWithStub.countAgentLists("AGENTCODE1").failed.futureValue

      error mustBe an[UpstreamErrorResponse]
      error.asInstanceOf[UpstreamErrorResponse].statusCode mustBe INTERNAL_SERVER_ERROR
    }

    "must fail when the downstream returns invalid JSON" in {
      wireMockServer.stubFor(post(urlEqualTo(stubUrl)).willReturn(okJson("""{ "unexpected": true }""")))

      connectorWithStub.countAgentLists("AGENTCODE1").failed.futureValue.getMessage.toLowerCase must include("unsubmitted")
    }
  }
}
