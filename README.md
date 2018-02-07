# API Documentation service

[ ![Download](https://api.bintray.com/packages/hmrc/releases/api-documentation/images/download.svg) ](https://bintray.com/hmrc/releases/api-documentation/_latestVersion)

This service is responsible for providing the API Documentation Frontend with the definitions and documentation artefacts
for APIs that are published onto the API Platform.


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")


### What uses this service?

* API Documentation Frontend

### What does this service use?

* API Definition - called to obtain the list of available APIs and the definition for a single API
* Service Locator - called to discover the location where the microservice providing a given API can be found 
* API microservices - called to obtain the documentation resources (RAML and schemas) for a given API version
* API Documentation - the service in production calls across to its counterpart in the sandbox environment to obtain API definitions and documentation resources for APIs that exist in that environment

### Running the service locally

You can use Service Manager to run the service locally on port `9980`:
* `sm --start API_DOCUMENTATION`

You can also run the service locally in stub mode. In this mode it will connect to dependent services on 
port `11111` - use either `api-services-stub` or `mocked-external-services-suite` to provide stubbed implementations.
To run in stub mode on port `9980`:
* `./run_in_stub_mode.sh`
