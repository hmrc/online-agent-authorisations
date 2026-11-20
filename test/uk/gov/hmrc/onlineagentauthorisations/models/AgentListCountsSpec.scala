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

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, Json}

class AgentListCountsSpec extends AnyWordSpec with Matchers {

  "AgentListCounts (JSON)" should {
    "read the counts" in {
      Json.obj("unsubmitted" -> 2, "submitted" -> 3).as[AgentListCounts] mustBe AgentListCounts(unsubmitted = 2, submitted = 3)
    }

    "write the counts" in {
      Json.toJson(AgentListCounts(0, 0)) mustBe Json.obj("unsubmitted" -> 0, "submitted" -> 0)
    }

    "fail to read when a count is missing" in {
      Json.obj("unsubmitted" -> 2).validate[AgentListCounts] mustBe a[JsError]
    }

    "fail to read when a count is not a number" in {
      Json.obj("unsubmitted" -> "two", "submitted" -> 3).validate[AgentListCounts] mustBe a[JsError]
    }
  }
}
