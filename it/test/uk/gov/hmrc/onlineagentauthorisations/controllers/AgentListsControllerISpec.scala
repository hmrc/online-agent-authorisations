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

package uk.gov.hmrc.onlineagentauthorisations.controllers

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status.*
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.http.test.WireMockSupport

class AgentListsControllerISpec
  extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with WireMockSupport
    with GuiceOneServerPerSuite {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port"                             -> wireMockPort,
        "microservice.services.online-agent-authorisations-stub.port" -> wireMockPort,
        "features.stub-oaa-exb-enabled"                               -> true,
        "http-verbs.retries.enabled"                                  -> false
      )
      .build()

  private lazy val ws: WSClient = app.injector.instanceOf[WSClient]

  private lazy val countAgentListsUrl = s"http://localhost:$port/online-agent-authorisations/count-agent-lists"
  private val stubUrl                 = "/online-agent-authorisations-stub/count-agent-lists"

  private def authReturns(body: JsObject): Unit =
    wireMockServer.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(okJson(body.toString)))

  private val agent = Json.obj(
    "internalId"          -> "internal-id",
    "optionalCredentials" -> Json.obj("providerId" -> "cred-id", "providerType" -> "GovernmentGateway"),
    "agentCode"           -> "AGENTCODE1"
  )

  private def countAgentLists(agentCode: String): WSResponse =
    ws.url(countAgentListsUrl)
      .withHttpHeaders("Authorization" -> "Bearer 123")
      .post(Json.obj("agentCode" -> agentCode))
      .futureValue

  "POST /online-agent-authorisations/count-agent-lists" should {

    "return 200 and the counts from the stub for a signed-in agent" in {
      authReturns(agent)
      wireMockServer.stubFor(
        post(urlEqualTo(stubUrl))
          .withRequestBody(equalToJson("""{ "agentCode": "AGENTCODE1" }"""))
          .willReturn(okJson("""{ "unsubmitted": 2, "submitted": 3 }"""))
      )

      val response = countAgentLists("AGENTCODE1")

      response.status mustBe OK
      response.json mustBe Json.obj("unsubmitted" -> 2, "submitted" -> 3)
    }

    "return 500 when the stub returns 500" in {
      authReturns(agent)
      wireMockServer.stubFor(post(urlEqualTo(stubUrl)).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

      countAgentLists("AGENTCODE1").status mustBe INTERNAL_SERVER_ERROR
    }

    "return 403 when the agent code is not the signed-in agent's" in {
      authReturns(agent)

      countAgentLists("SOMEONEELSE").status mustBe FORBIDDEN
      wireMockServer.verify(0, postRequestedFor(urlEqualTo(stubUrl)))
    }

    "return 403 when auth returns no agent code" in {
      authReturns(agent - "agentCode")

      countAgentLists("AGENTCODE1").status mustBe FORBIDDEN
    }

    "return 401 when there is no active session" in {
      wireMockServer.stubFor(
        post(urlEqualTo("/auth/authorise"))
          .willReturn(aResponse().withStatus(UNAUTHORIZED).withHeader("WWW-Authenticate", """MDTP detail="MissingBearerToken""""))
      )

      countAgentLists("AGENTCODE1").status mustBe UNAUTHORIZED
    }

    "return 403 when the user is not an agent" in {
      wireMockServer.stubFor(
        post(urlEqualTo("/auth/authorise"))
          .willReturn(aResponse().withStatus(UNAUTHORIZED).withHeader("WWW-Authenticate", """MDTP detail="UnsupportedAffinityGroup""""))
      )

      countAgentLists("AGENTCODE1").status mustBe FORBIDDEN
    }
  }
}
