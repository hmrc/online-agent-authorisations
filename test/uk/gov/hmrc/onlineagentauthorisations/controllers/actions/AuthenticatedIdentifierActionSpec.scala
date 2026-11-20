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

package uk.gov.hmrc.onlineagentauthorisations.controllers.actions

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.mvc.{BodyParsers, Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AuthenticatedIdentifierActionSpec extends AnyFreeSpec with Matchers {

  private implicit val system: ActorSystem       = ActorSystem("AuthenticatedIdentifierActionSpec")
  private implicit val materializer: Materializer = Materializer(system)
  private implicit val ec: ExecutionContext      = ExecutionContext.global

  private type Retrieved = Option[String] ~ Option[Credentials] ~ Option[String]

  private class FakeAuthConnector(result: Future[Retrieved]) extends AuthConnector {
    override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
      result.asInstanceOf[Future[A]]
  }

  private def retrieved(internalId: Option[String], credentials: Option[Credentials], agentCode: Option[String]): Future[Retrieved] =
    Future.successful(new ~(new ~(internalId, credentials), agentCode))

  private val credentials = Credentials("cred-id", "GovernmentGateway")

  // Echoes what the action put on the request
  private def run(authResult: Future[Retrieved]): Future[Result] = {
    val action = new AuthenticatedIdentifierAction(new FakeAuthConnector(authResult), new BodyParsers.Default(stubPlayBodyParsers))
    action.invokeBlock(FakeRequest(), r => Future.successful(Results.Ok(s"${r.credentialId}|${r.agentCode}")))
  }

  "AuthenticatedIdentifierAction" - {

    "must let an agent with an internal ID, credentials and agent code through" in {
      val result = run(retrieved(Some("internal-id"), Some(credentials), Some("AGENTCODE1")))

      status(result) mustBe OK
      contentAsString(result) mustBe "cred-id|AGENTCODE1"
    }

    "must return 403 when there is no agent code" in {
      status(run(retrieved(Some("internal-id"), Some(credentials), None))) mustBe FORBIDDEN
    }

    "must return 403 when the agent code is empty" in {
      status(run(retrieved(Some("internal-id"), Some(credentials), Some("")))) mustBe FORBIDDEN
    }

    "must return 403 when there are no credentials" in {
      status(run(retrieved(Some("internal-id"), None, Some("AGENTCODE1")))) mustBe FORBIDDEN
    }

    "must return 403 when there is no internal ID" in {
      status(run(retrieved(None, Some(credentials), Some("AGENTCODE1")))) mustBe FORBIDDEN
    }

    "must return 401 when there is no active session" in {
      status(run(Future.failed(MissingBearerToken()))) mustBe UNAUTHORIZED
    }

    "must return 403 when the user is not an agent" in {
      status(run(Future.failed(UnsupportedAffinityGroup()))) mustBe FORBIDDEN
    }
  }
}
