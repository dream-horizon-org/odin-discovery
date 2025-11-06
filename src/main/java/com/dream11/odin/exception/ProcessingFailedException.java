package com.dream11.odin.exception;

import com.dream11.rest.exception.RestException;

public class ProcessingFailedException extends RestException {
  public ProcessingFailedException(String errorCode, String message, int httpStatusCode) {
    super(errorCode, message, httpStatusCode);
  }
}
