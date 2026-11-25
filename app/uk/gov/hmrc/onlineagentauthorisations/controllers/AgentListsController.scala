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

import play.api.Logging
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.onlineagentauthorisations.controllers.actions.IdentifierAction
import uk.gov.hmrc.onlineagentauthorisations.models.CountAgentListsRequest
import uk.gov.hmrc.onlineagentauthorisations.service.AgentListsService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton()
class AgentListsController @Inject()(
                                      cc: ControllerComponents,
                                      service: AgentListsService,
                                      auth: IdentifierAction
                                    )(implicit ec: ExecutionContext) extends BackendController(cc) with Logging {

  def countAgentLists: Action[JsValue] = auth.async(parse.json) { implicit request =>
    request.body
      .validate[CountAgentListsRequest]
      .fold(
        errs =>
          Future.successful(BadRequest(Json.obj("message" -> "Invalid payload", "errors" -> JsError.toJson(errs)))),
        body =>
          if (body.agentCode != request.agentCode) {
            logger.warn("[AgentListsController][countAgentLists] agent code in the request does not match the signed-in agent")
            Future.successful(Forbidden(Json.obj("message" -> "Agent code does not match the signed-in agent")))
          } else {
            service
              .countAgentLists(body.agentCode)
              .map(counts => Ok(Json.toJson(counts)))
              .recover {
                case u: UpstreamErrorResponse =>
                  Status(u.statusCode)(Json.obj("message" -> u.message))
                case NonFatal(t) =>
                  logger.error("[AgentListsController][countAgentLists] failed", t)
                  InternalServerError(Json.obj("message" -> "Unexpected error"))
              }
          }
      )
  }
}
