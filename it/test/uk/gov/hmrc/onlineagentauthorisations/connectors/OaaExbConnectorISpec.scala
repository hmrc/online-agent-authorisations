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

import com.fasterxml.jackson.core.JsonParseException
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.http.Fault
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.{HeaderCarrier, JsValidationException, UpstreamErrorResponse}
import uk.gov.hmrc.onlineagentauthorisations.models.AgentListCounts
import uk.gov.hmrc.onlineagentauthorisations.utils.WireMockHelper

class OaaExbConnectorISpec
  extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneAppPerSuite
    with WireMockHelper {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private def appConfig(stubEnabled: Boolean): Map[String, Any] = Map(
    "microservice.services.online-agent-authorisations-stub.protocol" -> "http",
    "microservice.services.online-agent-authorisations-stub.host"     -> "localhost",
    "microservice.services.online-agent-authorisations-stub.port"     -> server.port(),
    "microservice.services.oaa-exb.protocol"                          -> "http",
    "microservice.services.oaa-exb.host"                              -> "localhost",
    "microservice.services.oaa-exb.port"                              -> server.port(),
    "features.stub-oaa-exb-enabled"                                   -> stubEnabled,
    "http-verbs.retries.enabled"                                      -> false
  )

  // The server has started by the time the lazy app is built
  override def fakeApplication(): Application =
    new GuiceApplicationBuilder().configure(appConfig(stubEnabled = true)).build()

  private lazy val connector = app.injector.instanceOf[OaaExbConnector]

  private val stubUrl   = "/online-agent-authorisations-stub/count-agent-lists"
  private val oaaExbUrl = "/oaa-exb/count-agent-lists"

  private val testAgentCode = "AGENTCODE1"

  private def requestBody(agentCode: String): String = Json.obj("agentCode" -> agentCode).toString()

  private val validCountsJson: JsValue = Json.obj(
    "unsubmitted" -> 2,
    "submitted"   -> 3
  )

  private def stubCountAgentLists(status: Int, body: String, agentCode: String = testAgentCode, url: String = stubUrl): Unit =
    server.stubFor(
      post(urlPathEqualTo(url))
        .withRequestBody(equalToJson(requestBody(agentCode)))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(body)
        )
    )

  "OaaExbConnector Integration Tests" - {

    "countAgentLists" - {

      "must return AgentListCounts when the stub returns 200 OK with valid JSON" in {
        stubCountAgentLists(200, validCountsJson.toString())

        val result = connector.countAgentLists(testAgentCode).futureValue

        result mustBe AgentListCounts(unsubmitted = 2, submitted = 3)

        server.verify(
          postRequestedFor(urlPathEqualTo(stubUrl))
            .withRequestBody(equalToJson(requestBody(testAgentCode)))
        )
      }

      "must return zero counts when the agent has no requests" in {
        stubCountAgentLists(200, Json.obj("unsubmitted" -> 0, "submitted" -> 0).toString(), agentCode = "NOREQUESTS")

        val result = connector.countAgentLists("NOREQUESTS").futureValue

        result mustBe AgentListCounts(unsubmitted = 0, submitted = 0)
      }

      "must call oaa-exb instead of the stub when stub-oaa-exb-enabled is false" in {
        stubCountAgentLists(200, validCountsJson.toString(), url = oaaExbUrl)

        val oaaExbApp = new GuiceApplicationBuilder().configure(appConfig(stubEnabled = false)).build()

        try {
          val result = oaaExbApp.injector.instanceOf[OaaExbConnector].countAgentLists(testAgentCode).futureValue

          result mustBe AgentListCounts(unsubmitted = 2, submitted = 3)

          server.verify(1, postRequestedFor(urlPathEqualTo(oaaExbUrl)))
          server.verify(0, postRequestedFor(urlPathEqualTo(stubUrl)))
        } finally {
          oaaExbApp.stop().futureValue
        }
      }

      "must throw UpstreamErrorResponse when the stub returns 400 Bad Request" in {
        stubCountAgentLists(400, "Invalid payload")

        val result = connector.countAgentLists(testAgentCode).failed.futureValue

        result mustBe an[UpstreamErrorResponse]
        result.asInstanceOf[UpstreamErrorResponse].statusCode mustBe 400

        server.verify(postRequestedFor(urlPathEqualTo(stubUrl)))
      }

      "must throw UpstreamErrorResponse when the stub returns 404 Not Found" in {
        stubCountAgentLists(404, "Not Found")

        val result = connector.countAgentLists(testAgentCode).failed.futureValue

        result mustBe an[UpstreamErrorResponse]
        result.asInstanceOf[UpstreamErrorResponse].statusCode mustBe 404
      }

      "must throw UpstreamErrorResponse when the stub returns 500 Internal Server Error" in {
        stubCountAgentLists(500, "Internal Server Error")

        val result = connector.countAgentLists(testAgentCode).failed.futureValue

        result mustBe an[UpstreamErrorResponse]
        result.asInstanceOf[UpstreamErrorResponse].statusCode mustBe 500

        server.verify(postRequestedFor(urlPathEqualTo(stubUrl)))
      }

      "must throw UpstreamErrorResponse when the stub returns 502 Bad Gateway" in {
        stubCountAgentLists(502, "Bad Gateway")

        val result = connector.countAgentLists(testAgentCode).failed.futureValue

        result mustBe an[UpstreamErrorResponse]
        result.asInstanceOf[UpstreamErrorResponse].statusCode mustBe 502
      }

      "must throw UpstreamErrorResponse when the stub returns 503 Service Unavailable" in {
        stubCountAgentLists(503, "Service Unavailable")

        val result = connector.countAgentLists(testAgentCode).failed.futureValue

        result mustBe an[UpstreamErrorResponse]
        result.asInstanceOf[UpstreamErrorResponse].statusCode mustBe 503
      }

      "must handle connection errors when the stub is unavailable" in {
        server.stubFor(
          post(urlPathEqualTo(stubUrl))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
        )

        val result = connector.countAgentLists(testAgentCode).failed.futureValue

        result mustBe a[Throwable]
      }

      "must handle a malformed JSON response" in {
        stubCountAgentLists(200, "{invalid json}")

        val result = connector.countAgentLists(testAgentCode).failed.futureValue

        result mustBe a[JsonParseException]
      }

      "must handle a JSON response with missing fields" in {
        stubCountAgentLists(200, Json.obj("unsubmitted" -> 2).toString())

        val result = connector.countAgentLists(testAgentCode).failed.futureValue

        result mustBe a[JsValidationException]
      }

      "must send the agent code as JSON" in {
        stubCountAgentLists(200, validCountsJson.toString())

        connector.countAgentLists(testAgentCode).futureValue

        server.verify(
          postRequestedFor(urlPathEqualTo(stubUrl))
            .withHeader("Content-Type", containing("application/json"))
            .withRequestBody(equalToJson(requestBody(testAgentCode)))
        )
      }

      "must make a POST request to the correct endpoint" in {
        stubCountAgentLists(200, validCountsJson.toString())

        connector.countAgentLists(testAgentCode).futureValue

        server.verify(1, postRequestedFor(urlPathEqualTo(stubUrl)))
      }

      "must not make multiple requests for a single call" in {
        stubCountAgentLists(200, validCountsJson.toString())

        connector.countAgentLists(testAgentCode).futureValue

        server.verify(1, postRequestedFor(urlPathEqualTo(stubUrl)))
        server.verify(0, getRequestedFor(anyUrl()))
      }
    }
  }
}
