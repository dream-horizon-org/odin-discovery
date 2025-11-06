package com.dream11.odin.constant;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Constants {
  public static final String MYSQL_CLIENT = "__mysql_client__";
  public static final String WEB_CLIENT = "__web_client__";
  public static final long DEFAULT_TTL_IN_SECONDS = 60L;
  public static final String PROVIDER_ACCOUNT_SERVICE = "__provider_account_service__";
  public static final String DISCOVERY_PROVIDER_SERVICE_CATEGORY = "DISCOVERY";
  public static final String IS_ACTIVE = "isActive";
  public static final String APP_CONFIG = "__app_config__";
  public static final String REST_VERTICLE = "com.dream11.odin.verticle.RestVerticle";
  public static final String ORG_ID_HEADER = "orgId";
}
