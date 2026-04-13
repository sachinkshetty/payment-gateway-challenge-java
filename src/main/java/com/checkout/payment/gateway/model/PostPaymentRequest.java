package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.Objects;

public class PostPaymentRequest implements Serializable {

  @JsonProperty("cardNumber")
  @NotBlank(message = "Card number is required")
  @Pattern(regexp = "^[0-9]{14,19}$", message = "Card number must be 14-19 digits")
  private String cardNumber;

  @JsonProperty("expiry_month")
  @Min(value = 1, message = "Expiry month must be between 1 and 12")
  @Max(value = 12, message = "Expiry month must be between 1 and 12")
  private int expiryMonth;

  @JsonProperty("expiry_year")
  private int expiryYear;

  @NotBlank(message = "Currency is required")
  @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code (e.g., GBP, USD)")
  private String currency;

  @Min(value = 1, message = "Amount must be at least 1 (in minor currency units, e.g., 1 = $0.01 USD)")
  private int amount;

  @Min(value = 100, message = "CVV must be 3 or 4 digits")
  @Max(value = 9999, message = "CVV must be 3 or 4 digits")
  private int cvv;

  public String getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(String cardNumber) {
    this.cardNumber = cardNumber;
  }

  public int getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(int expiryMonth) {
    this.expiryMonth = expiryMonth;
  }

  public int getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(int expiryYear) {
    this.expiryYear = expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public int getAmount() {
    return amount;
  }

  public void setAmount(int amount) {
    this.amount = amount;
  }

  public int getCvv() {
    return cvv;
  }

  public void setCvv(int cvv) {
    this.cvv = cvv;
  }

  @JsonProperty("expiry_date")
  public String getExpiryDate() {
    return String.format("%d/%d", expiryMonth, expiryYear);
  }

  @Override
  public String toString() {
    return "PostPaymentRequest{" +
        "cardNumber=" + cardNumber +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        ", cvv=" + cvv +
        '}';
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    PostPaymentRequest that = (PostPaymentRequest) o;
    return expiryMonth == that.expiryMonth && expiryYear == that.expiryYear && amount == that.amount
        && cvv == that.cvv && Objects.equals(cardNumber, that.cardNumber) && Objects.equals(
        currency, that.currency);
  }

  @Override
  public int hashCode() {
    return Objects.hash(cardNumber, expiryMonth, expiryYear, currency, amount, cvv);
  }
}
