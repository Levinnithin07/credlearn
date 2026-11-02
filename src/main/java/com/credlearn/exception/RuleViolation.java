package com.credlearn.exception;

public class RuleViolation extends RuntimeException {
  public RuleViolation(String message) {
    super(message);
  }
}
