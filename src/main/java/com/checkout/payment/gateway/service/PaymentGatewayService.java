package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankException;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);
  private static final String BANK_API_URL = "http://localhost:8080/payments";

  private final PaymentsRepository paymentsRepository;

  @Autowired
  protected RestTemplate restTemplate;

  public PaymentGatewayService(PaymentsRepository paymentsRepository) {
    this.paymentsRepository = paymentsRepository;
  }

  /**
   *  This method returns the payment details for the id .
   * @param id - ID of the payment
   * @return PostPaymentResponse - contains the Payment Response
   */
  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to to payment with ID {}", id);
    return paymentsRepository.get(id).orElseThrow(() -> new EventProcessingException("Invalid ID"));
  }

  /**
   *  This method process payment request by passing payment request to the external bank for authorization of the payment
   * @param paymentRequest:PostPaymentRequest - containing the payment request fields
   * @return PostPaymentResponse - containing the payment response
   */
  public PostPaymentResponse processPayment(PostPaymentRequest paymentRequest) {
    LOG.debug("Processing payment request: {}", paymentRequest);
    try {
      BankPaymentRequest bankPaymentRequest = toBankPaymentRequest(paymentRequest);
      ResponseEntity<BankPaymentResponse> response = restTemplate.postForEntity(
          BANK_API_URL, bankPaymentRequest, BankPaymentResponse.class);

      PostPaymentResponse postPaymentResponse = toPostPaymentResponse(
          Objects.requireNonNull(response.getBody()), paymentRequest);
      paymentsRepository.add(postPaymentResponse);
      return postPaymentResponse;
    } catch (HttpClientErrorException ex) {
      throw new BankException("Client error from bank API: " + ex.getResponseBodyAsString(),
          ex.getStatusCode().value()
      );
    } catch (HttpServerErrorException ex) {
      throw new BankException("Server error from bank API", ex.getStatusCode().value());
    } catch (ResourceAccessException ex) {
      throw new BankException("Bank service unavailable", 503);
    }
  }

  /**
   * This method transforms the PostPaymentRequest to BankPaymentRequest to be sent to the Bank
   * @param paymentRequest - PostPaymentRequest - containing the payment request fields
   * @return BankPaymentRequest - containing the request to be sent to Bank
   */
  private BankPaymentRequest toBankPaymentRequest(PostPaymentRequest paymentRequest) {
    return new BankPaymentRequest(paymentRequest.getCardNumber(), paymentRequest.getExpiryDate(),
        paymentRequest.getCurrency(), paymentRequest.getAmount(), paymentRequest.getCvv());
  }

  /**
   *  This method transforms the Bank Response to Post Payment response to be sent to the merchants
   * @param bankPaymentResponse - BankPaymentResponse response from the banks
   * @param postPaymentRequest - PostPaymentRequest request fields from the merchant
   * @return PostPaymentResponse - PostPaymentResponse response to be sent to the merchants
   */
  private PostPaymentResponse toPostPaymentResponse(BankPaymentResponse bankPaymentResponse,
      PostPaymentRequest postPaymentRequest) {
    PostPaymentResponse postPaymentResponse = new PostPaymentResponse();
    if (bankPaymentResponse.authorized) {
      postPaymentResponse.setStatus(PaymentStatus.AUTHORIZED);
    } else {
      postPaymentResponse.setStatus(PaymentStatus.DECLINED);
    }
    postPaymentResponse.setId(UUID.randomUUID());
    postPaymentResponse.setCurrency(postPaymentRequest.getCurrency());
    postPaymentResponse.setAmount(postPaymentRequest.getAmount());
    postPaymentResponse.setExpiryMonth(postPaymentRequest.getExpiryMonth());
    postPaymentResponse.setExpiryYear(postPaymentRequest.getExpiryYear());
    postPaymentResponse.setCardNumberLastFour(Integer.parseInt(
        postPaymentRequest.getCardNumber()
            .substring(postPaymentRequest.getCardNumber().length() - 4)));

    return postPaymentResponse;
  }
}
