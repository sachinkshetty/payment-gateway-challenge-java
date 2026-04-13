package com.checkout.payment.gateway.model;

import java.util.Objects;

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

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    BankPaymentResponse that = (BankPaymentResponse) o;
    return authorized == that.authorized && Objects.equals(authorization_code,
        that.authorization_code);
  }

  @Override
  public int hashCode() {
    return Objects.hash(authorized, authorization_code);
  }
}
