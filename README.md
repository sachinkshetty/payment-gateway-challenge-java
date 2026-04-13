# Instructions for candidates

This is the Java version of the Payment Gateway challenge. If you haven't already read this [README.md](https://github.com/cko-recruitment/) on the details of this exercise, please do so now.

## Requirements
- JDK 17
- Docker

## Template structure

src/ - A skeleton SpringBoot Application

test/ - Some simple JUnit tests

imposters/ - contains the bank simulator configuration. Don't change this

.editorconfig - don't change this. It ensures a consistent set of rules for submissions when reformatting code

docker-compose.yml - configures the bank simulator


## API Documentation
For documentation openAPI is included, and it can be found under the following url: **http://localhost:8090/swagger-ui/index.html**

**Feel free to change the structure of the solution, use a different library etc.**

## Instructions 

- start the application - ./gradlew bootRun
- run the tests - ./gradlew clean test
- github actions - https://github.com/sachinkshetty/payment-gateway-challenge-java/actions

### Endpoints
- swagger endpoint - http://localhost:8090/swagger-ui/index.html#

### Documentation

- I have created 2 endpoints - GET endpoint (/payment/{id}) for getting processed payments from the database and 
POST endpoint (/payment) for posting payments to bank 
- I have made changes to the PostPaymentRequest class in model directory. I have changed the cardNumber
  field to be 14 to 19 digits long and the same is sent to the bank, but I return only last 4 digits as part of response
- Service class post the payment request to bank and the response from the bank is saved in the database along with the payment request fields.
- I have written unit test for the controller class, service class and the validator class. There is also an integration test class
  PaymentGatewayControllerTest to test end to end scenario. PaymentGatewayControllerTest uses @SpringBootTest and MockRestServiceServer 
  to test the interaction with the bank. 
- I used the impostors to see the examples (i.e api request and response) and to use them for creating test data for the tests.

