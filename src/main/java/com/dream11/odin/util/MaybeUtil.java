package com.dream11.odin.util;

import io.reactivex.rxjava3.core.MaybeTransformer;
import java.util.concurrent.atomic.AtomicLong;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.slf4j.Logger;

@UtilityClass
public final class MaybeUtil {

  /** Operator which adds debug logs to a Single. */
  public <T> MaybeTransformer<T, T> applyDebugLogs(Logger log, String logPrefix) {
    AtomicLong startTime = new AtomicLong();
    return single ->
        single
            .doOnSubscribe(
                disposable -> {
                  startTime.set(System.currentTimeMillis());
                  log.debug("{} Subscribed", logPrefix);
                })
            .doOnSuccess(
                result -> {
                  long elapsedTime = System.currentTimeMillis() - startTime.get();
                  log.debug("{} Received after {}ms {}", logPrefix, elapsedTime, result);
                })
            .doOnError(
                err -> {
                  long elapsedTime = System.currentTimeMillis() - startTime.get();
                  log.error(
                      "{} Error after {}ms {}", logPrefix, elapsedTime, err.getMessage(), err);
                });
  }

  /** Operator which adds debug logs to a Single. */
  public <T> MaybeTransformer<T, T> applyDebugLogs(Logger log) {
    val logPrefix = Thread.currentThread().getStackTrace()[3].getMethodName();
    return applyDebugLogs(log, logPrefix);
  }
}
