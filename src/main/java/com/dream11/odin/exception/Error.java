package com.dream11.odin.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum Error {
  INTERNAL_SERVER_ERROR("E01", "Internal server error", HttpStatus.SC_INTERNAL_SERVER_ERROR),
  INVALID_DISCOVERY_PROVIDER("E02", "Invalid discovery provider type", HttpStatus.SC_BAD_REQUEST),

  DISCOVERY_SERVICE_NOT_FOUND(
      "E03", "No active discovery service found for any account", HttpStatus.SC_BAD_REQUEST),
  ACTION_NOT_SUPPORTED("E04", "Action not supported", HttpStatus.SC_BAD_REQUEST),
  RECORD_NOT_PRESENT("E05", "Record not present", HttpStatus.SC_NOT_FOUND),
  PARTIAL_MATCHING_RECORD("E06", "Record not matching the details", HttpStatus.SC_NOT_FOUND),
  RECORD_TYPE_CANNOT_CHANGE_ONCE_CREATED(
      "E07", "Record type cannot change once created", HttpStatus.SC_BAD_REQUEST),
  RECORD_VALUE_CANNOT_BE_EMPTY("E08", "Record values cannot be empty", HttpStatus.SC_BAD_REQUEST),
  ACCOUNT_NAME_CANNOT_BE_EMPTY("E09", "Account name cannot be empty", HttpStatus.SC_BAD_REQUEST),
  DUPLICATE_ID_FOUND_IN_RECORD_ACTIONS(
      "E10", "Duplicate ID found in record actions", HttpStatus.SC_BAD_REQUEST),
  RECORD_NAME_CANNOT_BE_EMPTY("E11", "Record name cannot be empty", HttpStatus.SC_BAD_REQUEST),
  RECORD_CANNOT_BE_EMPTY("E12", "Record cannot be empty", HttpStatus.SC_BAD_REQUEST),
  WEIGHTED_RECORD_SHOULD_HAVE_WEIGHT(
      "E13", "Weighted record should have a weight between 0 and 100", HttpStatus.SC_BAD_REQUEST),
  IDENTIFIER_IS_REQUIRED_FOR_WEIGHTED_RECORD(
      "E14", "Identifier is required for weighted record", HttpStatus.SC_BAD_REQUEST),
  RECORD_ACTIONS_CANNOT_BE_EMPTY(
      "E15", "Record actions cannot be empty", HttpStatus.SC_BAD_REQUEST),
  RECORD_ID_CANNOT_BE_EMPTY("E16", "Record ID cannot be empty", HttpStatus.SC_BAD_REQUEST),
  ACTION_REQUIRED_IN_RECORD_ACTION(
      "E17", "Action in record action is required", HttpStatus.SC_BAD_REQUEST),
  RECORD_TYPE_VALIDATION(
      "E18", "Record type can only be simple or weighted", HttpStatus.SC_BAD_REQUEST),
  TTL_INVALID(
      "E19", "Invalid TTL,should be greater than or equal to zero", HttpStatus.SC_BAD_REQUEST),
  NO_DISCOVERY_PROVIDER_FOUND(
      "E20", "No matching active provider found", HttpStatus.SC_BAD_REQUEST),

  MULTIPLE_DISCOVERY_PROVIDER(
      "E21", "multiple active discovery provider found for domain: %s", HttpStatus.SC_BAD_REQUEST),
  UPDATE_RECORD_FAILED(
      "E22", "Updating TTL or weight for record failed", HttpStatus.SC_INTERNAL_SERVER_ERROR);

  private final String errorCode;
  private final String errorMessage;
  private final int httpStatusCode;
}
