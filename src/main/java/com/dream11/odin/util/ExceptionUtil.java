package com.dream11.odin.util;

import com.dream11.odin.exception.Error;
import com.dream11.odin.exception.ProcessingFailedException;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ExceptionUtil {

  public static ProcessingFailedException getException(Error error, Object... params) {
    String message = String.format(error.getErrorMessage(), params);
    return new ProcessingFailedException(error.getErrorCode(), message, error.getHttpStatusCode());
  }
}
