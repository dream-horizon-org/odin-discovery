package com.dream11.odin.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ApplicationConfig {

  public void validate() {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    Set<ConstraintViolation<ApplicationConfig>> constraintViolations = validator.validate(this);
    if (!constraintViolations.isEmpty()) {
      throw new ConstraintViolationException(constraintViolations);
    }
  }

  @NotNull OdinAccountManagerConfig odinAccountManagerConfig;

  @NotNull List<String> tags;

  String awsSTSEndpoint;

  String awsEndpointPort;

  String awsRoute53Endpoint;

  String awsRegion;

  int port;

  int awsRetryCount;
}
