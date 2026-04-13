package com.checkout.payment.gateway.validation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.YearMonth;

class ExpiryDateValidatorTest {

  @Test
  void shouldReturnTrueWhenExpiryDateIsInFuture() {
    YearMonth future = YearMonth.now().plusMonths(6);
    assertTrue(ExpiryDateValidator.isFutureDate(future.getMonthValue(), future.getYear()));
  }

  @Test
  void shouldReturnFalseWhenExpiryDateIsInPast() {
    YearMonth past = YearMonth.now().minusMonths(6);
    assertFalse(ExpiryDateValidator.isFutureDate(past.getMonthValue(), past.getYear()));
  }

  @Test
  void shouldReturnFalseWhenExpiryDateIsCurrentMonth() {
    YearMonth today = YearMonth.now();
    assertFalse(ExpiryDateValidator.isFutureDate(today.getMonthValue(), today.getYear()));
  }
}
