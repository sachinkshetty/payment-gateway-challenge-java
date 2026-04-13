package com.checkout.payment.gateway.validation;

import java.time.YearMonth;

public class ExpiryDateValidator {

  private ExpiryDateValidator() {

  }

  public static boolean isFutureDate(int expiryMonth, int expiryYear) {
    YearMonth expiryYearMonth = YearMonth.of(expiryYear, expiryMonth);
    YearMonth today = YearMonth.now();
    return expiryYearMonth.isAfter(today);
  }
}
