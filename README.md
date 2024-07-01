
# digital-services-tax

## About
The Digital Services Tax (DST) digital service is split into a number of different microservices all serving specific functions which are listed below:

**Frontend** - The main frontend for the service which includes the pages for registration, returns and an account home page.

**Backend** - The service that the frontend uses to call HOD APIs to retrieve and send information relating to business information and subscribing to regime.

**Stub** - Microservice that is used to mimic the DES APIs when running services locally or in the development and staging environments.

This is the backend service, which acts as an adaptor between the frontend, and the ETMP APIs. It also handles the enrolment callback from tax-enrolments, and provides ROSM data. 

For details about the digital services tax see [the GOV.UK guidance](https://www.gov.uk/government/consultations/digital-services-tax-draft-guidance)

## APIs

###GET  /lookup-company

Using a UTR from Enrolments, this retrieves a registered company from ROSM, or returns a 404 if no organisation record is found.

###GET  /lookup-company/:utr/:postcode

Retrieves a company and it's address from ROSM with a provided utr. If the retrieved company's postcode matches the parameter then the company is retrieved. If no company is found or if the postcodes do not match then a 404 is returned.  

###POST /registration

Submits a registration to ETMP. Fails if a registration has already been submitted for a given safeId.

###GET  /registration

Retrieves a registration from ETMP if it exists, or returns 404 if no record exists.                 

###POST /returns/:periodKey

Submits a return to ETMP which is assigned to a periodKey. A return with the same periodKey can be sent again, this allows the user to make an adjustment.

###GET  /returns

Returns a list of all returns held on ETMP.  

###POST /tax-enrolment-callback/:subscriptionId

Handles the callback from tax enrolments when the registration is activated.    

###GET /tax-enrolment/groupId/:groupId

Returns the Dst reference number if exists for the group/organisation.

See [here](https://github.com/HMRC/tax-enrolments#put-tax-enrolmentssubscriptionssubscriptionidissuer) and [here](https://github.com/HMRC/tax-enrolments#put-tax-enrolmentssubscriptionssubscriptionidsubscriber) for details

## Running the service
### Service manager
The whole service can be started with:

`sm2 --start DST_ALL`

or specifically for only the backend

`sm2 --start DST`

### Locally

`sbt 'run 8741'`

* Visit http://localhost:9949/auth-login-stub/gg-sign-in
* You may need to add some user details to the form:
#### DST Registration journey
    * Affinity Group: Organisation
    * Enrolment Key: IR-CT
    * Identifier Name: UTR
    * Identifier Value: 1111111000
* Then enter a redirect url: http://localhost:8740/digital-services-tax
* Press **Submit**.

#### DST Returns Journey
    * Affinity Group: Organisation
    * Group identifier: 12345
    * Enrolment Key: HMRC-DST-ORG
    * Identifier Name: DSTRefNumber
    * Identifier Value: AMDST0799721562
* Then enter a redirect url: http://localhost:8740/digital-services-tax
* Press **Submit**.

## Running to test changes using journey tests

In order to test backend changes, the service needs to be ran in line with the SM profile to ensure that the callback is triggered successfully (allowing for the return journey), you need to run the service using the following command:

`sbt run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes -Dconfig.resource=application.conf -Dmicroservice.services.tax-enrolments.enabled=false -Drun.mode=Dev`


## Running the tests

###Unit tests:

`sbt test`

###Integration tests:

`sbt it:test`

###All tests:

`sbt test it:test`

## Running scalafmt

To apply scalafmt formatting using the rules configured in the .scalafmt.conf, run:

`sbt scalafmtAll`

To check the files have been formatted correctly, run:

`sbt scalafmtCheckAll`

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
