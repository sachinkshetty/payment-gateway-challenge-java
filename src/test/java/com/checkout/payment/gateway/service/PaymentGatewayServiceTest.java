package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankException;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

  @Mock
  private PaymentsRepository paymentsRepository;

  @Mock
  private RestTemplate restTemplate;

  private PaymentGatewayService paymentGatewayService;

  @BeforeEach
  void setUp() {
    paymentGatewayService = new PaymentGatewayService(paymentsRepository);
    paymentGatewayService.restTemplate = restTemplate;
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

  private BankPaymentResponse createAuthorizedBankResponse() {
    BankPaymentResponse response = new BankPaymentResponse();
    response.setAuthorized(true);
    response.setAuthorization_code("AUTH123");
    return response;
  }

  private BankPaymentResponse createDeclinedBankResponse() {
    BankPaymentResponse response = new BankPaymentResponse();
    response.setAuthorized(false);
    response.setAuthorization_code(null);
    return response;
  }

  // GET PAYMENT BY ID TESTS
  @Test
  void shouldReturnPaymentWhenPaymentExistsInRepository() {
    UUID paymentId = UUID.randomUUID();
    PostPaymentResponse mockPayment = new PostPaymentResponse();
    mockPayment.setId(paymentId);
    mockPayment.setAmount(100);
    mockPayment.setCurrency("GBP");
    mockPayment.setStatus(PaymentStatus.AUTHORIZED);

    when(paymentsRepository.get(paymentId)).thenReturn(Optional.of(mockPayment));

    PostPaymentResponse result = paymentGatewayService.getPaymentById(paymentId);

    assertNotNull(result);
    assertEquals(paymentId, result.getId());
    assertEquals(100, result.getAmount());
    assertEquals("GBP", result.getCurrency());
    assertEquals(PaymentStatus.AUTHORIZED, result.getStatus());
    verify(paymentsRepository, times(1)).get(paymentId);
  }

  @Test
  void shouldThrowEventProcessingExceptionWhenPaymentDoesNotExist() {
    UUID paymentId = UUID.randomUUID();

    when(paymentsRepository.get(paymentId)).thenReturn(Optional.empty());

    EventProcessingException exception = assertThrows(EventProcessingException.class,
        () -> paymentGatewayService.getPaymentById(paymentId));

    assertEquals("Invalid ID", exception.getMessage());
    verify(paymentsRepository, times(1)).get(paymentId);
  }

  // PROCESS PAYMENT - AUTHORIZED TESTS
  @Test
  void shouldReturnAuthorizedStatusWhenBankAuthorizesPayment() {
    PostPaymentRequest request = createValidPaymentRequest();
    BankPaymentResponse bankResponse = createAuthorizedBankResponse();

    when(restTemplate.postForEntity(anyString(), any(BankPaymentRequest.class), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(bankResponse, HttpStatus.OK));

    PostPaymentResponse result = paymentGatewayService.processPayment(request);

    assertNotNull(result);
    assertEquals(PaymentStatus.AUTHORIZED, result.getStatus());
    assertEquals("GBP", result.getCurrency());
    assertEquals(100, result.getAmount());
    assertEquals(12, result.getExpiryMonth());
    assertEquals(2027, result.getExpiryYear());
    assertEquals(8877, result.getCardNumberLastFour());
    verify(paymentsRepository, times(1)).add(any(PostPaymentResponse.class));
  }

  @Test
  void shouldExtractLastFourDigitsOfCardNumber() {
    PostPaymentRequest request = createValidPaymentRequest();
    request.setCardNumber("1234567890123456");
    BankPaymentResponse bankResponse = createAuthorizedBankResponse();

    when(restTemplate.postForEntity(anyString(), any(BankPaymentRequest.class), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(bankResponse, HttpStatus.OK));

    PostPaymentResponse result = paymentGatewayService.processPayment(request);

    assertEquals(3456, result.getCardNumberLastFour());
  }

  @Test
  void shouldSavePaymentToRepositoryAfterSuccessfulAuthorization() {
    PostPaymentRequest request = createValidPaymentRequest();
    BankPaymentResponse bankResponse = createAuthorizedBankResponse();

    when(restTemplate.postForEntity(anyString(), any(BankPaymentRequest.class), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(bankResponse, HttpStatus.OK));

    paymentGatewayService.processPayment(request);

    ArgumentCaptor<PostPaymentResponse> captor = ArgumentCaptor.forClass(PostPaymentResponse.class);
    verify(paymentsRepository, times(1)).add(captor.capture());

    PostPaymentResponse savedPayment = captor.getValue();
    assertEquals(PaymentStatus.AUTHORIZED, savedPayment.getStatus());
    assertNotNull(savedPayment.getId());
  }

  // PROCESS PAYMENT - DECLINED TESTS
  @Test
  void shouldReturnDeclinedStatusWhenBankDeclinesPayment() {
    PostPaymentRequest request = createValidPaymentRequest();
    BankPaymentResponse bankResponse = createDeclinedBankResponse();

    when(restTemplate.postForEntity(anyString(), any(BankPaymentRequest.class), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(bankResponse, HttpStatus.OK));

    PostPaymentResponse result = paymentGatewayService.processPayment(request);

    assertNotNull(result);
    assertEquals(PaymentStatus.DECLINED, result.getStatus());
    verify(paymentsRepository, times(1)).add(any(PostPaymentResponse.class));
  }

  @Test
  void shouldSaveDeclinedPaymentToRepository() {
    PostPaymentRequest request = createValidPaymentRequest();
    BankPaymentResponse bankResponse = createDeclinedBankResponse();

    when(restTemplate.postForEntity(anyString(), any(BankPaymentRequest.class), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(bankResponse, HttpStatus.OK));

    paymentGatewayService.processPayment(request);

    ArgumentCaptor<PostPaymentResponse> captor = ArgumentCaptor.forClass(PostPaymentResponse.class);
    verify(paymentsRepository, times(1)).add(captor.capture());

    PostPaymentResponse savedPayment = captor.getValue();
    assertEquals(PaymentStatus.DECLINED, savedPayment.getStatus());
  }

  // BANK REQUEST MAPPING TESTS
  @Test
  void shouldMapPostPaymentRequestToBankPaymentRequestCorrectly() {
    PostPaymentRequest request = createValidPaymentRequest();
    BankPaymentResponse bankResponse = createAuthorizedBankResponse();

    when(restTemplate.postForEntity(anyString(), any(BankPaymentRequest.class), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(bankResponse, HttpStatus.OK));

    paymentGatewayService.processPayment(request);

    ArgumentCaptor<BankPaymentRequest> captor = ArgumentCaptor.forClass(BankPaymentRequest.class);
    verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(BankPaymentResponse.class));

    BankPaymentRequest bankRequest = captor.getValue();
    assertEquals("2222405343248877", bankRequest.getCardNumber());
    assertEquals("12/2027", bankRequest.getExpiryDate());
    assertEquals("GBP", bankRequest.getCurrency());
    assertEquals(100, bankRequest.getAmount());
    assertEquals(123, bankRequest.getCvv());
  }

  @Test
  void shouldCallBankAPIWithCorrectEndpoint() {
    PostPaymentRequest request = createValidPaymentRequest();
    BankPaymentResponse bankResponse = createAuthorizedBankResponse();

    when(restTemplate.postForEntity(anyString(), any(BankPaymentRequest.class), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(bankResponse, HttpStatus.OK));

    paymentGatewayService.processPayment(request);

    verify(restTemplate).postForEntity(
        eq("http://localhost:8080/payments"),
        any(BankPaymentRequest.class),
        eq(BankPaymentResponse.class)
    );
  }

  // ERROR HANDLING TESTS
  @Test
  void shouldThrowBankExceptionWhenBankReturns400Error() {
    PostPaymentRequest request = createValidPaymentRequest();

    when(restTemplate.postForEntity(anyString(), any(BankPaymentRequest.class), eq(BankPaymentResponse.class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request"));

    BankException exception = assertThrows(BankException.class,
        () -> paymentGatewayService.processPayment(request));

    assertEquals(400, exception.getStatusCode());
    assertTrue(exception.getMessage().contains("Client error from bank API"));
    verify(paymentsRepository, never()).add(any());
  }

  @Test
  void shouldThrowBankExceptionWhenBankReturns500Error() {
    PostPaymentRequest request = createValidPaymentRequest();

    when(restTemplate.postForEntity(anyString(), any(BankPaymentRequest.class), eq(BankPaymentResponse.class)))
        .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error"));

    BankException exception = assertThrows(BankException.class,
        () -> paymentGatewayService.processPayment(request));

    assertEquals(500, exception.getStatusCode());
    assertEquals("Server error from bank API", exception.getMessage());
    verify(paymentsRepository, never()).add(any());
  }

  @Test
  void shouldThrowBankExceptionWithStatus503WhenBankServiceIsUnavailable() {
    PostPaymentRequest request = createValidPaymentRequest();

    when(restTemplate.postForEntity(anyString(), any(BankPaymentRequest.class), eq(BankPaymentResponse.class)))
        .thenThrow(new ResourceAccessException("Connection refused"));

    BankException exception = assertThrows(BankException.class,
        () -> paymentGatewayService.processPayment(request));

    assertEquals(503, exception.getStatusCode());
    assertEquals("Bank service unavailable", exception.getMessage());
    verify(paymentsRepository, never()).add(any());
  }

  // RESPONSE MAPPING TESTS
  @Test
  void shouldMapBankResponseToPostPaymentResponseCorrectly() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("4111111111111111");
    request.setCurrency("USD");
    request.setAmount(2500);
    request.setCvv(456);
    request.setExpiryMonth(6);
    request.setExpiryYear(2025);

    BankPaymentResponse bankResponse = createAuthorizedBankResponse();

    when(restTemplate.postForEntity(anyString(), any(BankPaymentRequest.class), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(bankResponse, HttpStatus.OK));

    PostPaymentResponse result = paymentGatewayService.processPayment(request);

    assertEquals("USD", result.getCurrency());
    assertEquals(2500, result.getAmount());
    assertEquals(6, result.getExpiryMonth());
    assertEquals(2025, result.getExpiryYear());
    assertEquals(1111, result.getCardNumberLastFour());
  }

  @Test
  void shouldGenerateUniqueIdForEachPayment() {
    PostPaymentRequest request = createValidPaymentRequest();
    BankPaymentResponse bankResponse = createAuthorizedBankResponse();

    when(restTemplate.postForEntity(anyString(), any(BankPaymentRequest.class), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(bankResponse, HttpStatus.OK));

    PostPaymentResponse result1 = paymentGatewayService.processPayment(request);
    PostPaymentResponse result2 = paymentGatewayService.processPayment(request);

    assertNotNull(result1.getId());
    assertNotNull(result2.getId());
    assertNotEquals(result1.getId(), result2.getId());
  }

  // MULTIPLE PAYMENTS TESTS
  @Test
  void shouldProcessMultiplePaymentsSequentially() {
    PostPaymentRequest request1 = createValidPaymentRequest();
    request1.setAmount(100);
    
    PostPaymentRequest request2 = createValidPaymentRequest();
    request2.setAmount(200);

    BankPaymentResponse bankResponse = createAuthorizedBankResponse();

    when(restTemplate.postForEntity(anyString(), any(BankPaymentRequest.class), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(bankResponse, HttpStatus.OK));

    PostPaymentResponse result1 = paymentGatewayService.processPayment(request1);
    PostPaymentResponse result2 = paymentGatewayService.processPayment(request2);

    assertEquals(100, result1.getAmount());
    assertEquals(200, result2.getAmount());
    verify(paymentsRepository, times(2)).add(any(PostPaymentResponse.class));
  }

  @Test
  void shouldHandleConsecutiveAuthorizationAndDeclinedPayments() {
    PostPaymentRequest request = createValidPaymentRequest();

    BankPaymentResponse authorizedResponse = createAuthorizedBankResponse();
    BankPaymentResponse declinedResponse = createDeclinedBankResponse();

    when(restTemplate.postForEntity(anyString(), any(BankPaymentRequest.class), eq(BankPaymentResponse.class)))
        .thenReturn(new ResponseEntity<>(authorizedResponse, HttpStatus.OK))
        .thenReturn(new ResponseEntity<>(declinedResponse, HttpStatus.OK));

    PostPaymentResponse result1 = paymentGatewayService.processPayment(request);
    PostPaymentResponse result2 = paymentGatewayService.processPayment(request);

    assertEquals(PaymentStatus.AUTHORIZED, result1.getStatus());
    assertEquals(PaymentStatus.DECLINED, result2.getStatus());
    verify(paymentsRepository, times(2)).add(any(PostPaymentResponse.class));
  }
}
