package com.dream11.odin.testUtils;

import io.grpc.Context;

public final class GrpcTestKeys {
  private GrpcTestKeys() {}

  public static final Context.Key<String> ORG_ID_KEY = Context.key("orgid");
}
