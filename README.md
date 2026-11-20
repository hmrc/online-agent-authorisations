# online-agent-authorisations

This is the backend for Online Agent Authorisations (OAA). It sits between [online-agent-authorisations-frontend](https://github.com/hmrc/online-agent-authorisations-frontend) and oaa-exb, which holds the agents' authorisation requests.

Locally, and in any environment without oaa-exb, calls go to [online-agent-authorisations-stub](https://github.com/hmrc/online-agent-authorisations-stub) instead (see [Stubbing oaa-exb](#stubbing-oaa-exb)).

## Running the service
Before starting, you will need to have [service-manager](https://github.com/hmrc/service-manager) installed/configured

### Dependencies
All dependencies can be found in [AppDependencies.scala](project/AppDependencies.scala)

### Running locally:
Service Manager:
- Start dependent services `sm2 --start OAA_ALL`
- Stop this service `sm2 --stop ONLINE_AGENT_AUTHORISATIONS`
- Start the server locally on `port 20111` with `sbt run`

The service runs at http://localhost:20111/online-agent-authorisations

### Testing:
- Run unit tests: `sbt test`
- Run integration tests: `sbt it/test`
- To run all tests and coverage: `sbt clean compile coverage test it/test coverageOff coverageReport`
- To run the service in test-only mode: `sbt run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes`

## Authentication

Every endpoint uses `IdentifierAction` ([AuthenticatedIdentifierAction](app/uk/gov/hmrc/onlineagentauthorisations/controllers/actions/AuthenticatedIdentifierAction.scala)). It lets a request through when the user is signed in with the `Agent` affinity group and has an internal ID, credentials and an agent code.

| Result | When |
|---|---|
| `401 Unauthorized` | There is no active session |
| `403 Forbidden` | The user is not an agent, or is missing an internal ID, credentials or agent code |

Where a request body contains an agent code, it must match the signed-in agent's agent code, otherwise the endpoint returns `403 Forbidden`. This stops one agent reading another agent's data.

## Structure

Each endpoint follows the same layers:

```
app/uk/gov/hmrc/onlineagentauthorisations/
├── controllers/          # validate the request, call the service, map errors to statuses
├── controllers/actions/  # auth
├── service/              # business logic
├── connectors/           # calls to oaa-exb (or the stub)
└── models/               # request and response models
```

Errors from downstream are handled the same way on every endpoint:

| Downstream result | Response |
|---|---|
| Success | `200 OK` with the response as JSON |
| Invalid request body | `400 Bad Request` |
| 4xx/5xx from downstream | The same status, with the message |
| Any other failure | `500 Internal Server Error` |

## Stubbing oaa-exb

The connector chooses where to send calls with a feature flag in [application.conf](conf/application.conf):

```
features {
  stub-oaa-exb-enabled = true
}
```

| Flag | Calls go to |
|---|---|
| `true` | `online-agent-authorisations-stub` (`microservice.services.online-agent-authorisations-stub`) |
| `false` | `oaa-exb` (`microservice.services.oaa-exb`) |

The path after the base URL is the same for both, so a new endpoint only needs adding to the stub with the same path.

## Adding a new endpoint

1. Add the request and response models to `models/`, with a `Json.format`, and a spec for each.
2. Add the call to the connector, using the stub/oaa-exb base path.
3. Add a service method that calls the connector.
4. Add a controller action wrapped in `auth`, and the route to [app.routes](conf/app.routes).
5. Add the same path to `online-agent-authorisations-stub`, with stub data.
6. Add unit tests for the controller and service, a WireMock spec for the connector, and an integration test in `it/`.

### License

This code is open source software licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).
