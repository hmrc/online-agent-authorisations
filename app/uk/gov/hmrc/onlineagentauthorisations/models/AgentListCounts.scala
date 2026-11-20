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

package uk.gov.hmrc.onlineagentauthorisations.models

import play.api.libs.json.{Json, OFormat}

/** How many unsubmitted requests, and requests waiting for an authorisation code, an agent has. */
case class AgentListCounts(unsubmitted: Int, submitted: Int)

object AgentListCounts {
  implicit val format: OFormat[AgentListCounts] = Json.format[AgentListCounts]
}
