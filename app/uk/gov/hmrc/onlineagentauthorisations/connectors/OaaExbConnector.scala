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

import play.api.Logging
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.*
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.onlineagentauthorisations.models.{AgentListCounts, CountAgentListsRequest}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class OaaExbConnector @Inject()(http: HttpClientV2, config: ServicesConfig)(implicit ec: ExecutionContext) extends Logging {

  private val stubPath: String   = config.baseUrl("online-agent-authorisations-stub") + "/online-agent-authorisations-stub"
  private val oaaExbPath: String = config.baseUrl("oaa-exb") + "/oaa-exb"

  val stubOaaExbEnabled: Boolean = config.getBoolean("features.stub-oaa-exb-enabled")

  private val basePath: String = if (stubOaaExbEnabled) stubPath else oaaExbPath

  def countAgentLists(agentCode: String)(implicit hc: HeaderCarrier): Future[AgentListCounts] =
    http
      .post(url"$basePath/count-agent-lists")
      .withBody(Json.toJson(CountAgentListsRequest(agentCode)))
      .execute[AgentListCounts]
      .recoverWith {
        case NonFatal(e) =>
          logger.error(s"[OaaExbConnector][countAgentLists] failed: ${e.getMessage}")
          Future.failed(e)
      }
}
