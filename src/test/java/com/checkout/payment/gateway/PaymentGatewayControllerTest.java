package com.checkout.payment.gateway;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;

  @Autowired
  PaymentsRepository paymentsRepository;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private RestTemplate restTemplate;

  private MockRestServiceServer mockServer;

  @BeforeEach
  void setUp() {
    mockServer = MockRestServiceServer.createServer(restTemplate);
  }

  private PostPaymentRequest createValidPaymentRequest() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("2222405343248877");
    request.setCurrency("GBP");
    request.setAmount(100);
    request.setCvv(123);
    request.setExpiryMonth(12);
    request.setExpiryYear(2027);
    return request;
  }

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setAmount(10);
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2024);
    payment.setCardNumberLastFour(4321);

    paymentsRepository.add(payment);

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(payment.getStatus().getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(payment.getCardNumberLastFour()))
        .andExpect(jsonPath("$.expiryMonth").value(payment.getExpiryMonth()))
        .andExpect(jsonPath("$.expiryYear").value(payment.getExpiryYear()))
        .andExpect(jsonPath("$.currency").value(payment.getCurrency()))
        .andExpect(jsonPath("$.amount").value(payment.getAmount()));
  }

  @Test
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/payment/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Page not found"));
  }

  @Test
  void shouldProcessPaymentSuccessfullyWhenBankAuthorizesTransaction() throws Exception {
    PostPaymentRequest paymentRequest = createValidPaymentRequest("2222405343248877");

    String bankResponseJson = "{" +
        "\"authorized\": true," +
        "\"authorization_code\": \"AUTH123456\"" +
        "}";

    mockServer.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andRespond(withSuccess(bankResponseJson, MediaType.APPLICATION_JSON));

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(paymentRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(PaymentStatus.AUTHORIZED.getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value("8877"))
        .andExpect(jsonPath("$.expiryMonth").value(12))
        .andExpect(jsonPath("$.expiryYear").value(2027))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(100))
        .andExpect(jsonPath("$.id").exists());

    mockServer.verify();
  }

  @Test
  void shouldReturnDeclinedStatusWhenBankDeclines() throws Exception {
    PostPaymentRequest paymentRequest = createValidPaymentRequest("2222405343248878");

    String bankResponseJson = "{" +
        "\"authorized\": false," +
        "\"authorization_code\":  \"\"" +
        "}";

    mockServer.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andRespond(withSuccess(bankResponseJson, MediaType.APPLICATION_JSON));

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(paymentRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(PaymentStatus.DECLINED.getName()))
        .andExpect(jsonPath("$.id").exists());

    mockServer.verify();
  }

  @Test
  void shouldReturnServiceUnavailableWhenBankServerIsDown() throws Exception {
    PostPaymentRequest paymentRequest = createValidPaymentRequest("2222405343248877");

    mockServer.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(paymentRequest)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value("Server error from bank API"))
        .andExpect(jsonPath("$.status").value(500));

    mockServer.verify();
  }

  @Test
  void shouldReturnBadRequestWhenBankReturns400Error() throws Exception {
    PostPaymentRequest paymentRequest = createValidPaymentRequest();

    String bankResponseJson = "{" +
        "\"error_message\": \"Not all required properties were sent in the request\"" +
        "}";

    mockServer.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST)
            .body(bankResponseJson)
            .contentType(MediaType.APPLICATION_JSON));

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(paymentRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Client error from bank API: " + bankResponseJson))
        .andExpect(jsonPath("$.status").value(400));

    mockServer.verify();
  }

  @Test
  void shouldReturnServiceUnavailableWhenBankConnectionTimesOut() throws Exception {
    PostPaymentRequest paymentRequest = createValidPaymentRequest("2222405343248877");

    mockServer.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andRespond(request -> {
          throw new org.springframework.web.client.ResourceAccessException("Connection timeout");
        });

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(paymentRequest)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message").value("Bank service unavailable"))
        .andExpect(jsonPath("$.status").value(503));

    mockServer.verify();
  }

  @Test
  void shouldReturnInternalServerErrorWhenBankReturns500WithErrorMessage() throws Exception {
    PostPaymentRequest paymentRequest = createValidPaymentRequest("2222405343248877");

    String errorResponse = "{\"error\": \"Internal Server Error\"}";

    mockServer.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorResponse)
            .contentType(MediaType.APPLICATION_JSON));

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(paymentRequest)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value("Server error from bank API"))
        .andExpect(jsonPath("$.status").value(500));

    mockServer.verify();
  }

  @Test
  void shouldPostTwoPaymentsAndRetrieveBothWithCorrectValues() throws Exception {
    // Create first payment request
    PostPaymentRequest paymentRequest1 = new PostPaymentRequest();
    paymentRequest1.setCardNumber("2222405343248877");
    paymentRequest1.setCurrency("GBP");
    paymentRequest1.setAmount(500);
    paymentRequest1.setCvv(123);
    paymentRequest1.setExpiryMonth(12);
    paymentRequest1.setExpiryYear(2027);

    // Create second payment request
    PostPaymentRequest paymentRequest2 = new PostPaymentRequest();
    paymentRequest2.setCardNumber("4111111111111111");
    paymentRequest2.setCurrency("USD");
    paymentRequest2.setAmount(1050);
    paymentRequest2.setCvv(456);
    paymentRequest2.setExpiryMonth(6);
    paymentRequest2.setExpiryYear(2027);

    // Bank response for both payments (authorized)
    String bankResponseJson = "{" +
        "\"authorized\": true," +
        "\"authorization_code\": \"AUTH123456\"" +
        "}";

    // Mock bank API calls for both payments
    mockServer.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andRespond(withSuccess(bankResponseJson, MediaType.APPLICATION_JSON));

    mockServer.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andRespond(withSuccess(bankResponseJson, MediaType.APPLICATION_JSON));

    // POST first payment
    MvcResult result1 = mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(paymentRequest1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(PaymentStatus.AUTHORIZED.getName()))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(500))
        .andExpect(jsonPath("$.expiryMonth").value(12))
        .andExpect(jsonPath("$.expiryYear").value(2027))
        .andExpect(jsonPath("$.cardNumberLastFour").value("8877"))
        .andExpect(jsonPath("$.id").exists())
        .andReturn();

    // Extract payment ID from first response
    String response1 = result1.getResponse().getContentAsString();
    String paymentId1 = JsonPath.read(response1, "$.id");

    // POST second payment
    MvcResult result2 = mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(paymentRequest2)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(PaymentStatus.AUTHORIZED.getName()))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.amount").value(1050))
        .andExpect(jsonPath("$.expiryMonth").value(6))
        .andExpect(jsonPath("$.expiryYear").value(2027))
        .andExpect(jsonPath("$.cardNumberLastFour").value("1111"))
        .andExpect(jsonPath("$.id").exists())
        .andReturn();

    // Extract payment ID from second response
    String response2 = result2.getResponse().getContentAsString();
    String paymentId2 = JsonPath.read(response2, "$.id");

    // GET first payment and verify values match the POST request
    mvc.perform(MockMvcRequestBuilders.get("/payment/" + paymentId1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(paymentId1))
        .andExpect(jsonPath("$.status").value(PaymentStatus.AUTHORIZED.getName()))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(500))
        .andExpect(jsonPath("$.expiryMonth").value(12))
        .andExpect(jsonPath("$.expiryYear").value(2027))
        .andExpect(jsonPath("$.cardNumberLastFour").value("8877"));

    // GET second payment and verify values match the POST request
    mvc.perform(MockMvcRequestBuilders.get("/payment/" + paymentId2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(paymentId2))
        .andExpect(jsonPath("$.status").value(PaymentStatus.AUTHORIZED.getName()))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.amount").value(1050))
        .andExpect(jsonPath("$.expiryMonth").value(6))
        .andExpect(jsonPath("$.expiryYear").value(2027))
        .andExpect(jsonPath("$.cardNumberLastFour").value("1111"));

    mockServer.verify();
  }

  private PostPaymentRequest createValidPaymentRequest(final String creditCard) {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber(creditCard);
    request.setCurrency("GBP");
    request.setAmount(100);
    request.setCvv(123);
    request.setExpiryMonth(12);
    request.setExpiryYear(2027);
    return request;
  }


}
