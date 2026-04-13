package com.checkout.payment.gateway.model;

public class BankPaymentResponse {

  public boolean authorized;

  public String authorization_code;

  public boolean isAuthorized() {
    return authorized;
  }

  public void setAuthorized(boolean authorized) {
    this.authorized = authorized;
  }

  public String getAuthorization_code() {
    return authorization_code;
  }

  public void setAuthorization_code(String authorization_code) {
    this.authorization_code = authorization_code;
  }

  @Override
  public String toString() {
    return "BankPaymentResponse{" +
        "isAuthorized='" + authorized + '\'' +
        ", authorization_code='" + authorization_code + '\'' +
        '}';
  }
}
