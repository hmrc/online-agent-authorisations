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

package uk.gov.hmrc.onlineagentauthorisations.service

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.onlineagentauthorisations.connectors.OaaExbConnector
import uk.gov.hmrc.onlineagentauthorisations.models.AgentListCounts

import scala.concurrent.Future

class AgentListsServiceSpec extends AnyFreeSpec with Matchers with ScalaFutures with MockitoSugar {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  "AgentListsService" - {

    "countAgentLists" - {

      "must delegate to the connector and return the counts" in {
        val connector = mock[OaaExbConnector]
        when(connector.countAgentLists(eqTo("AGENTCODE1"))(any[HeaderCarrier]))
          .thenReturn(Future.successful(AgentListCounts(2, 3)))

        new AgentListsService(connector).countAgentLists("AGENTCODE1").futureValue mustBe AgentListCounts(2, 3)

        verify(connector, times(1)).countAgentLists(eqTo("AGENTCODE1"))(any[HeaderCarrier])
      }

      "must propagate a failure from the connector" in {
        val connector = mock[OaaExbConnector]
        when(connector.countAgentLists(any[String])(any[HeaderCarrier]))
          .thenReturn(Future.failed(UpstreamErrorResponse("boom", 500)))

        new AgentListsService(connector).countAgentLists("AGENTCODE1").failed.futureValue mustBe an[UpstreamErrorResponse]
      }
    }
  }
}
