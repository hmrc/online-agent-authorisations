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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.onlineagentauthorisations.controllers.actions.FakeIdentifierAction
import uk.gov.hmrc.onlineagentauthorisations.models.AgentListCounts
import uk.gov.hmrc.onlineagentauthorisations.service.AgentListsService

import scala.concurrent.{ExecutionContext, Future}

class AgentListsControllerSpec extends AnyFreeSpec with Matchers with MockitoSugar {

  private implicit val system: ActorSystem       = ActorSystem("AgentListsControllerSpec")
  private implicit val materializer: Materializer = Materializer(system)
  private implicit val ec: ExecutionContext      = ExecutionContext.global

  private val cc = stubControllerComponents()

  private def controller(service: AgentListsService): AgentListsController =
    new AgentListsController(cc, service, new FakeIdentifierAction(cc.parsers))

  private def post(service: AgentListsService, body: JsValue): Future[Result] =
    controller(service).countAgentLists(
      FakeRequest(POST, "/count-agent-lists").withHeaders(CONTENT_TYPE -> JSON).withBody(body)
    )

  private def serviceReturning(result: Future[AgentListCounts]): AgentListsService = {
    val service = mock[AgentListsService]
    when(service.countAgentLists(any[String])(any[HeaderCarrier])).thenReturn(result)
    service
  }

  private val validBody = Json.obj("agentCode" -> FakeIdentifierAction.agentCode)

  "AgentListsController.countAgentLists" - {

    "must return 200 and the counts for the signed-in agent" in {
      val service = serviceReturning(Future.successful(AgentListCounts(2, 3)))

      val result = post(service, validBody)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.obj("unsubmitted" -> 2, "submitted" -> 3)
      verify(service).countAgentLists(eqTo(FakeIdentifierAction.agentCode))(any[HeaderCarrier])
    }

    "must return 200 with 0 and 0" in {
      val result = post(serviceReturning(Future.successful(AgentListCounts(0, 0))), validBody)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.obj("unsubmitted" -> 0, "submitted" -> 0)
    }

    "must return 400 when the payload is invalid" in {
      val service = serviceReturning(Future.successful(AgentListCounts(0, 0)))

      val result = post(service, Json.obj("somethingElse" -> "x"))

      status(result) mustBe BAD_REQUEST
      (contentAsJson(result) \ "message").as[String] mustBe "Invalid payload"
      verify(service, never()).countAgentLists(any[String])(any[HeaderCarrier])
    }

    "must return 403 when the agent code is not the signed-in agent's" in {
      val service = serviceReturning(Future.successful(AgentListCounts(0, 0)))

      val result = post(service, Json.obj("agentCode" -> "SOMEONEELSE"))

      status(result) mustBe FORBIDDEN
      verify(service, never()).countAgentLists(any[String])(any[HeaderCarrier])
    }

    "must pass on the status of an upstream error" in {
      val result = post(serviceReturning(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND))), validBody)

      status(result) mustBe NOT_FOUND
      (contentAsJson(result) \ "message").as[String] mustBe "not found"
    }

    "must return 500 for an upstream 500" in {
      val result = post(serviceReturning(Future.failed(UpstreamErrorResponse("boom", INTERNAL_SERVER_ERROR))), validBody)

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "must return 500 for any other failure" in {
      val result = post(serviceReturning(Future.failed(new RuntimeException("kaboom"))), validBody)

      status(result) mustBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "message").as[String] mustBe "Unexpected error"
    }
  }
}
