package com.checkout.payment.gateway.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.CommonExceptionHandler;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayControllerUnitTest {

  @Mock
  private PaymentGatewayService paymentGatewayService;

  @InjectMocks
  private PaymentGatewayController paymentGatewayController;

  private MockMvc mvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.standaloneSetup(paymentGatewayController)
        .setControllerAdvice(new CommonExceptionHandler())
        .build();
    objectMapper = new ObjectMapper();
  }

  private PostPaymentResponse createMockPaymentResponse() {
    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(UUID.randomUUID());
    response.setStatus(PaymentStatus.AUTHORIZED);
    response.setCardNumberLastFour(8877);
    response.setExpiryMonth(12);
    response.setExpiryYear(2027);
    response.setCurrency("GBP");
    response.setAmount(100);
    return response;
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
  void shouldReturnPaymentWhenPaymentIdExists() throws Exception {
    UUID paymentId = UUID.randomUUID();
    PostPaymentResponse mockPayment = createMockPaymentResponse();
    mockPayment.setId(paymentId);

    when(paymentGatewayService.getPaymentById(paymentId)).thenReturn(mockPayment);

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + paymentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(paymentId.toString())))
        .andExpect(jsonPath("$.status", is(PaymentStatus.AUTHORIZED.getName())))
        .andExpect(jsonPath("$.cardNumberLastFour", is(8877)))
        .andExpect(jsonPath("$.expiryMonth", is(12)))
        .andExpect(jsonPath("$.expiryYear", is(2027)))
        .andExpect(jsonPath("$.currency", is("GBP")))
        .andExpect(jsonPath("$.amount", is(100)));
  }

  @Test
  void shouldReturnNotFoundWhenPaymentIdDoesNotExist() throws Exception {
    UUID paymentId = UUID.randomUUID();

    when(paymentGatewayService.getPaymentById(paymentId))
        .thenThrow(new EventProcessingException("Invalid ID"));

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + paymentId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message", is("Page not found")));
  }

  @Test
  void shouldReturnDifferentPaymentsForDifferentIds() throws Exception {
    UUID paymentIdOne = UUID.randomUUID();
    UUID paymentIdTwo = UUID.randomUUID();

    PostPaymentResponse payment1 = createMockPaymentResponse();
    payment1.setId(paymentIdOne);
    payment1.setAmount(100);

    PostPaymentResponse payment2 = createMockPaymentResponse();
    payment2.setId(paymentIdTwo);
    payment2.setAmount(500);

    when(paymentGatewayService.getPaymentById(paymentIdOne)).thenReturn(payment1);
    when(paymentGatewayService.getPaymentById(paymentIdTwo)).thenReturn(payment2);

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + paymentIdOne))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount", is(100)));

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + paymentIdTwo))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount", is(500)));
  }

  @Test
  void shouldProcessPaymentSuccessfully() throws Exception {
    PostPaymentRequest request = createValidPaymentRequest();
    PostPaymentResponse mockResponse = createMockPaymentResponse();

    when(paymentGatewayService.processPayment(any(PostPaymentRequest.class)))
        .thenReturn(mockResponse);

    mvc.perform(MockMvcRequestBuilders.post("/payment")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is(PaymentStatus.AUTHORIZED.getName())))
        .andExpect(jsonPath("$.cardNumberLastFour", is(8877)))
        .andExpect(jsonPath("$.expiryMonth", is(12)))
        .andExpect(jsonPath("$.expiryYear", is(2027)))
        .andExpect(jsonPath("$.currency", is("GBP")))
        .andExpect(jsonPath("$.amount", is(100)))
        .andExpect(jsonPath("$.id").exists());
  }

  @Test
  void shouldReturnDeclinedPaymentStatus() throws Exception {
    PostPaymentRequest request = createValidPaymentRequest();
    PostPaymentResponse mockResponse = createMockPaymentResponse();
    mockResponse.setStatus(PaymentStatus.DECLINED);

    when(paymentGatewayService.processPayment(any(PostPaymentRequest.class)))
        .thenReturn(mockResponse);

    mvc.perform(MockMvcRequestBuilders.post("/payment")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is(PaymentStatus.DECLINED.getName())));
  }

  @Test
  void shouldReturnBankExceptionWhenBankReturnsError() throws Exception {
    PostPaymentRequest request = createValidPaymentRequest();

    when(paymentGatewayService.processPayment(any(PostPaymentRequest.class)))
        .thenThrow(new com.checkout.payment.gateway.exception.BankException("Bank service unavailable", 500));

    mvc.perform(MockMvcRequestBuilders.post("/payment")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message", is("Bank service unavailable")))
        .andExpect(jsonPath("$.status", is(500)));
  }

  @Test
  void shouldAcceptMultipleConsecutivePaymentRequests() throws Exception {
    PostPaymentRequest request = createValidPaymentRequest();
    PostPaymentResponse mockResponse = createMockPaymentResponse();

    when(paymentGatewayService.processPayment(any(PostPaymentRequest.class)))
        .thenReturn(mockResponse);

    mvc.perform(MockMvcRequestBuilders.post("/payment")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    mvc.perform(MockMvcRequestBuilders.post("/payment")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  void shouldReturnCompletePaymentResponseWithAllFields() throws Exception {
    PostPaymentRequest request = createValidPaymentRequest();
    PostPaymentResponse mockResponse = createMockPaymentResponse();

    when(paymentGatewayService.processPayment(any(PostPaymentRequest.class)))
        .thenReturn(mockResponse);

    mvc.perform(MockMvcRequestBuilders.post("/payment")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.status").exists())
        .andExpect(jsonPath("$.cardNumberLastFour").exists())
        .andExpect(jsonPath("$.expiryMonth").exists())
        .andExpect(jsonPath("$.expiryYear").exists())
        .andExpect(jsonPath("$.currency").exists())
        .andExpect(jsonPath("$.amount").exists());
  }

  @Test
  void shouldReturnMapRequestFieldsCorrectly() throws Exception {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("1111222233334444");
    request.setCurrency("USD");
    request.setAmount(250);
    request.setCvv(456);
    request.setExpiryMonth(6);
    request.setExpiryYear(2030);

    PostPaymentResponse mockResponse = createMockPaymentResponse();
    mockResponse.setCurrency("USD");
    mockResponse.setAmount(250);
    mockResponse.setExpiryMonth(6);
    mockResponse.setExpiryYear(2030);
    mockResponse.setCardNumberLastFour(4444);

    when(paymentGatewayService.processPayment(any(PostPaymentRequest.class)))
        .thenReturn(mockResponse);

    mvc.perform(MockMvcRequestBuilders.post("/payment")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency", is("USD")))
        .andExpect(jsonPath("$.amount", is(250)))
        .andExpect(jsonPath("$.expiryMonth", is(6)))
        .andExpect(jsonPath("$.expiryYear", is(2030)))
        .andExpect(jsonPath("$.cardNumberLastFour", is(4444)));
  }

  @Test
  void shouldReturn200WhenExpiryDateIsNextMonth() throws Exception {
    PostPaymentRequest request = createValidPaymentRequest();
    request.setExpiryMonth(5);
    request.setExpiryYear(2027);

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  void shouldReturn400WhenExpiryDateIsInThePast() throws Exception {
    PostPaymentRequest request = createValidPaymentRequest();
    request.setExpiryMonth(1);
    request.setExpiryYear(2020);

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Card expiry date must be in the future"));
  }

  @Test
  void shouldReturn400WhenExpiryDateIsCurrentMonth() throws Exception {
    PostPaymentRequest request = createValidPaymentRequest();
    request.setExpiryMonth(4);
    request.setExpiryYear(2026);

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Card expiry date must be in the future"));
  }

  @Test
  void shouldReturn200WhenAmountIsMinimumValue1RepresentsOneMinorCurrencyUnit() throws Exception {
    PostPaymentRequest request = createValidPaymentRequest();
    request.setAmount(1);

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  void shouldReturn200WhenAmountIs1050Representing1050MinorUnits() throws Exception {
    PostPaymentRequest request = createValidPaymentRequest();
    request.setAmount(1050);

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  void shouldReturn400WhenAmountIsZero() throws Exception {
    PostPaymentRequest request = createValidPaymentRequest();
    request.setAmount(0);

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }
}
