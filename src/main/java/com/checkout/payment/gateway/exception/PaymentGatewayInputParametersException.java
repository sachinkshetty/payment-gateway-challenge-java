package com.checkout.payment.gateway.exception;

public class PaymentGatewayInputParametersException extends RuntimeException{
  private final int statusCode;

  public PaymentGatewayInputParametersException(String message, int statusCode) {
    super(message);
    this.statusCode = statusCode;
  }

  public int getStatusCode() {
    return statusCode;
  }
}
