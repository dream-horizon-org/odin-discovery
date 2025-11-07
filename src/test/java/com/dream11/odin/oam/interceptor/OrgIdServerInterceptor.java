package com.dream11.odin.oam.interceptor;

import com.dream11.odin.testUtils.GrpcTestKeys;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

public class OrgIdServerInterceptor implements ServerInterceptor {

  private static final Metadata.Key<String> ORG_ID_MD_KEY =
      Metadata.Key.of("orgid", Metadata.ASCII_STRING_MARSHALLER);

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    String orgId = headers.get(ORG_ID_MD_KEY);
    Context ctx = Context.current();
    if (orgId != null && !orgId.isBlank()) {
      ctx = ctx.withValue(GrpcTestKeys.ORG_ID_KEY, orgId);
    }
    return Contexts.interceptCall(ctx, call, headers, next);
  }
}
