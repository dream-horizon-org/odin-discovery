package com.dream11.odin.oam;

import com.dream11.odin.constant.TestConstants;
import com.dream11.odin.dto.v1.ProviderAccount;
import com.dream11.odin.dto.v1.ProviderServiceAccount;
import com.dream11.odin.grpc.provideraccount.v1.GetAllProviderAccountsRequest;
import com.dream11.odin.grpc.provideraccount.v1.GetAllProviderAccountsResponse;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountRequest;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountResponse;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountsRequest;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountsResponse;
import com.dream11.odin.grpc.provideraccount.v1.Rx3ProviderAccountServiceGrpc;
import com.dream11.odin.testUtils.GrpcTestKeys;
import com.dream11.odin.util.TestUtil;
import com.google.protobuf.Struct;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;

public class MockOAMProviderAccountService
    extends Rx3ProviderAccountServiceGrpc.ProviderAccountServiceImplBase {

  public static final String accountNameConsul = "consulAccount";
  public static final String accountNameAWS = "staging";
  public static final String accountNameAWSNoActive = "stagingNoActive";
  public static final String accountNameAWSMultipleActive = "stagingMultipleActive";

  public static final GetProviderAccountResponse accountWithConsulResponse;
  public static GetProviderAccountResponse accountWithAWSr53Response;
  public static GetProviderAccountResponse accountWithAWSr53ResponseNoActive;
  public static GetProviderAccountResponse accountWithAWSMultipleActive;
  public static GetAllProviderAccountsResponse accountWithAllProvidersResponse;

  public static GetAllProviderAccountsResponse accountWithAllProvidersResponseMultipleActive;
  public static GetAllProviderAccountsResponse emptyAccountsResponse;

  static {
    String oamAccountResponseFile = "oam/accountWithConsul.json";
    try {

      // consul account with hashicorp linked account
      ProviderAccount hashicorpAccount =
          ProviderAccount.newBuilder()
              .setName("hashicorp")
              .setProvider(TestConstants.HASHICORP_PROVIDER)
              .setCategory(TestConstants.HASHICORP_CATEGORY)
              .setData(TestUtil.jsonToProtoBuilder(new JsonObject(), Struct.newBuilder()))
              .addServices(
                  ProviderServiceAccount.newBuilder()
                      .setName("Consul")
                      .setCategory(TestConstants.CATEGORY_DISCOVERY)
                      .setData(
                          TestUtil.jsonToProtoBuilder(
                              new JsonObject()
                                  .put("host", "127.0.0.1")
                                  .put("port", "8082")
                                  .put(
                                      "domains",
                                      new JsonArray(
                                          "[{\"name\":\"example-stag.local\",\"isActive\":true}]"))
                                  .put("timeoutInSecs", 3),
                              Struct.newBuilder())))
              .build();

      ClassLoader classLoader = MockOAMProviderAccountService.class.getClassLoader();
      File file =
          new File(
              Objects.requireNonNull(classLoader.getResource(oamAccountResponseFile)).getFile());
      String content =
          FileUtils.readFileToString(new File(file.getAbsolutePath()), Charset.defaultCharset());
      ProviderAccount providerAccountMock =
          ProviderAccount.newBuilder()
              .setName("consulAccount")
              .setProvider("AWS")
              .setCategory("CLOUD")
              .setData(TestUtil.jsonToProtoBuilder(new JsonObject(), Struct.newBuilder()))
              .build();
      accountWithConsulResponse =
          TestUtil.jsonToProtoBuilder(
                  new JsonObject(content), GetProviderAccountResponse.newBuilder())
              .setAccount(providerAccountMock)
              .addLinkedAccounts(hashicorpAccount)
              .build();

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @SneakyThrows
  public static void buildAWSr53Response(String privateHostedZoneId, String publicHostedZoneId) {
    JsonArray domainsList =
        new JsonArray()
            .add(
                new JsonObject()
                    .put("id", privateHostedZoneId)
                    .put("name", "example.local")
                    .put("isActive", true))
            .add(
                new JsonObject()
                    .put("id", publicHostedZoneId)
                    .put("name", "example.com")
                    .put("isActive", true));
    ProviderAccount rout53AwsAccount =
        ProviderAccount.newBuilder()
            .setName("staging")
            .setProvider("AWS")
            .setCategory("CLOUD")
            .setData(TestUtil.jsonToProtoBuilder(new JsonObject(), Struct.newBuilder()))
            .addServices(
                ProviderServiceAccount.newBuilder()
                    .setName("R53")
                    .setCategory(TestConstants.CATEGORY_DISCOVERY)
                    .setData(
                        TestUtil.jsonToProtoBuilder(
                            new JsonObject()
                                .put(
                                    "assumeRoleARN", "arn:aws:iam::000000000000:role/testAdminRole")
                                .put("region", "us-east-1")
                                .put("domains", domainsList),
                            Struct.newBuilder())))
            .build();
    ClassLoader classLoader = MockOAMProviderAccountService.class.getClassLoader();
    String oamR53AccountResponseFile = "oam/accountWithR53.json";

    File file =
        new File(
            Objects.requireNonNull(classLoader.getResource(oamR53AccountResponseFile)).getFile());
    String content =
        FileUtils.readFileToString(new File(file.getAbsolutePath()), Charset.defaultCharset());
    content = content.replace("<public_hosted_zone_id_placeholder>", publicHostedZoneId);
    content = content.replace("<private_hosted_zone_id_placeholder>", privateHostedZoneId);

    accountWithAWSr53Response =
        TestUtil.jsonToProtoBuilder(
                new JsonObject(content), GetProviderAccountResponse.newBuilder())
            .setAccount(rout53AwsAccount)
            .build();
  }

  @SneakyThrows
  public static void buildAWSr53ResponseNoActive(
      String privateHostedZoneId, String publicHostedZoneId) {
    JsonArray domainsList =
        new JsonArray()
            .add(
                new JsonObject()
                    .put("id", privateHostedZoneId)
                    .put("name", "example.local")
                    .put("isActive", false))
            .add(
                new JsonObject()
                    .put("id", publicHostedZoneId)
                    .put("name", "example.com")
                    .put("isActive", false));
    ProviderAccount rout53AwsAccount =
        ProviderAccount.newBuilder()
            .setName("staging")
            .setProvider("AWS")
            .setCategory("CLOUD")
            .setData(TestUtil.jsonToProtoBuilder(new JsonObject(), Struct.newBuilder()))
            .addServices(
                ProviderServiceAccount.newBuilder()
                    .setName("R53")
                    .setCategory(TestConstants.CATEGORY_DISCOVERY)
                    .setData(
                        TestUtil.jsonToProtoBuilder(
                            new JsonObject()
                                .put(
                                    "assumeRoleARN", "arn:aws:iam::000000000000:role/testAdminRole")
                                .put("region", "us-east-1")
                                .put("domains", domainsList),
                            Struct.newBuilder())))
            .build();
    ClassLoader classLoader = MockOAMProviderAccountService.class.getClassLoader();
    String oamR53AccountResponseFile = "oam/accountWithR53.json";

    File file =
        new File(
            Objects.requireNonNull(classLoader.getResource(oamR53AccountResponseFile)).getFile());
    String content =
        FileUtils.readFileToString(new File(file.getAbsolutePath()), Charset.defaultCharset());
    content = content.replace("<public_hosted_zone_id_placeholder>", publicHostedZoneId);
    content = content.replace("<private_hosted_zone_id_placeholder>", privateHostedZoneId);

    accountWithAWSr53ResponseNoActive =
        TestUtil.jsonToProtoBuilder(
                new JsonObject(content), GetProviderAccountResponse.newBuilder())
            .setAccount(rout53AwsAccount)
            .build();
  }

  @SneakyThrows
  public static void buildAWSr53ResponseMultipleActive(
      String privateHostedZoneId, String publicHostedZoneId) {
    JsonArray domainsList =
        new JsonArray()
            .add(
                new JsonObject()
                    .put("id", privateHostedZoneId)
                    .put("name", "example.local")
                    .put("isActive", true))
            .add(
                new JsonObject()
                    .put("id", publicHostedZoneId)
                    .put("name", "example.com")
                    .put("isActive", true));
    ProviderAccount rout53AwsAccount =
        ProviderAccount.newBuilder()
            .setName("staging")
            .setProvider("AWS")
            .setCategory("CLOUD")
            .setData(TestUtil.jsonToProtoBuilder(new JsonObject(), Struct.newBuilder()))
            .addServices(
                ProviderServiceAccount.newBuilder()
                    .setName("R53")
                    .setCategory(TestConstants.CATEGORY_DISCOVERY)
                    .setData(
                        TestUtil.jsonToProtoBuilder(
                            new JsonObject()
                                .put(
                                    "assumeRoleARN", "arn:aws:iam::000000000000:role/testAdminRole")
                                .put("region", "us-east-1")
                                .put("domains", domainsList),
                            Struct.newBuilder())))
            .build();
    ClassLoader classLoader = MockOAMProviderAccountService.class.getClassLoader();
    String oamR53AccountResponseFile = "oam/accountWithR53.json";

    File file =
        new File(
            Objects.requireNonNull(classLoader.getResource(oamR53AccountResponseFile)).getFile());
    String content =
        FileUtils.readFileToString(new File(file.getAbsolutePath()), Charset.defaultCharset());
    content = content.replace("<public_hosted_zone_id_placeholder>", publicHostedZoneId);
    content = content.replace("<private_hosted_zone_id_placeholder>", privateHostedZoneId);

    accountWithAWSMultipleActive =
        TestUtil.jsonToProtoBuilder(
                new JsonObject(content), GetProviderAccountResponse.newBuilder())
            .setAccount(rout53AwsAccount)
            .build();
  }

  @SneakyThrows
  public static void buildAccountWithAllProvidersResponse(
      String privateHostedZoneId, String publicHostedZoneId) {
    JsonArray domainsList =
        new JsonArray()
            .add(
                new JsonObject()
                    .put("id", privateHostedZoneId)
                    .put("name", "example.local")
                    .put("isActive", true))
            .add(
                new JsonObject()
                    .put("id", publicHostedZoneId)
                    .put("name", "example.com")
                    .put("isActive", true));
    ProviderAccount rout53AwsAccount =
        ProviderAccount.newBuilder()
            .setName("staging")
            .setProvider("AWS")
            .setCategory("CLOUD")
            .setData(TestUtil.jsonToProtoBuilder(new JsonObject(), Struct.newBuilder()))
            .addServices(
                ProviderServiceAccount.newBuilder()
                    .setName("R53")
                    .setCategory(TestConstants.CATEGORY_DISCOVERY)
                    .setData(
                        TestUtil.jsonToProtoBuilder(
                            new JsonObject()
                                .put(
                                    "assumeRoleARN", "arn:aws:iam::000000000000:role/testAdminRole")
                                .put("region", "us-east-1")
                                .put("domains", domainsList),
                            Struct.newBuilder())))
            .build();
    ClassLoader classLoader = MockOAMProviderAccountService.class.getClassLoader();
    String oamR53AccountResponseFile = "oam/accountWithR53.json";

    File file =
        new File(
            Objects.requireNonNull(classLoader.getResource(oamR53AccountResponseFile)).getFile());
    String content =
        FileUtils.readFileToString(new File(file.getAbsolutePath()), Charset.defaultCharset());
    content = content.replace("<public_hosted_zone_id_placeholder>", publicHostedZoneId);
    content = content.replace("<private_hosted_zone_id_placeholder>", privateHostedZoneId);
    GetAllProviderAccountsResponse.Builder builder = GetAllProviderAccountsResponse.newBuilder();
    emptyAccountsResponse = builder.build();
    builder.addAccounts(
        TestUtil.jsonToProtoBuilder(
                new JsonObject(content), GetProviderAccountResponse.newBuilder())
            .setAccount(rout53AwsAccount)
            .build());

    ProviderAccount hashicorpAccount =
        ProviderAccount.newBuilder()
            .setName("hashicorp")
            .setProvider(TestConstants.HASHICORP_PROVIDER)
            .setCategory(TestConstants.HASHICORP_CATEGORY)
            .setData(TestUtil.jsonToProtoBuilder(new JsonObject(), Struct.newBuilder()))
            .addServices(
                ProviderServiceAccount.newBuilder()
                    .setName("Consul")
                    .setCategory(TestConstants.CATEGORY_DISCOVERY)
                    .setData(
                        TestUtil.jsonToProtoBuilder(
                            new JsonObject()
                                .put("host", "127.0.0.1")
                                .put("port", "8082")
                                .put(
                                    "domains",
                                    new JsonArray(
                                        "[{\"name\":\"example-stag.local\",\"isActive\":true}]"))
                                .put("timeoutInSecs", 3),
                            Struct.newBuilder())))
            .build();
    String oamAccountResponseFile = "oam/accountWithConsul.json";

    classLoader = MockOAMProviderAccountService.class.getClassLoader();
    file =
        new File(Objects.requireNonNull(classLoader.getResource(oamAccountResponseFile)).getFile());
    content =
        FileUtils.readFileToString(new File(file.getAbsolutePath()), Charset.defaultCharset());
    ProviderAccount providerAccountMock =
        ProviderAccount.newBuilder()
            .setName("consulAccount")
            .setProvider("AWS")
            .setCategory("CLOUD")
            .setData(TestUtil.jsonToProtoBuilder(new JsonObject(), Struct.newBuilder()))
            .build();
    builder.addAccounts(
        TestUtil.jsonToProtoBuilder(
                new JsonObject(content), GetProviderAccountResponse.newBuilder())
            .setAccount(providerAccountMock)
            .addLinkedAccounts(hashicorpAccount)
            .build());
    accountWithAllProvidersResponse = builder.build();
    // adding multiple active accounts for testing
    rout53AwsAccount =
        ProviderAccount.newBuilder()
            .setName("load")
            .setProvider("AWS")
            .setCategory("CLOUD")
            .setData(TestUtil.jsonToProtoBuilder(new JsonObject(), Struct.newBuilder()))
            .addServices(
                ProviderServiceAccount.newBuilder()
                    .setName("R53")
                    .setCategory(TestConstants.CATEGORY_DISCOVERY)
                    .setData(
                        TestUtil.jsonToProtoBuilder(
                            new JsonObject()
                                .put(
                                    "assumeRoleARN",
                                    "arn:aws:iam::000000000000:role/testAdminRoleLoad")
                                .put("region", "us-east-1")
                                .put("domains", domainsList),
                            Struct.newBuilder())))
            .build();
    builder.addAccounts(
        TestUtil.jsonToProtoBuilder(
                new JsonObject(content), GetProviderAccountResponse.newBuilder())
            .setAccount(rout53AwsAccount)
            .build());
    accountWithAllProvidersResponseMultipleActive = builder.build();
  }

  @Override
  public Single<GetProviderAccountResponse> getProviderAccount(
      Single<GetProviderAccountRequest> request) {
    return request.map(
        req -> {
          if (accountNameConsul.equals(req.getName())) {
            return accountWithConsulResponse;
          }
          if (accountNameAWS.equals(req.getName())) {
            return accountWithAWSr53Response;
          }
          if (accountNameAWSNoActive.equals(req.getName())) {
            return accountWithAWSr53ResponseNoActive;
          }
          if (accountNameAWSMultipleActive.equals(req.getName())) {
            return accountWithAWSMultipleActive;
          }
          return GetProviderAccountResponse.newBuilder().build();
        });
  }

  @Override
  public Single<GetProviderAccountsResponse> getProviderAccounts(
      Single<GetProviderAccountsRequest> request) {
    return request.map(
        req ->
            GetProviderAccountsResponse.newBuilder()
                .addAccounts(accountWithConsulResponse)
                .build());
  }

  @Override
  public Single<GetAllProviderAccountsResponse> getAllProviderAccounts(
      GetAllProviderAccountsRequest request) {
    String orgId = GrpcTestKeys.ORG_ID_KEY.get();
    if (orgId != null && !orgId.isBlank()) {
      switch (orgId) {
        case "1" -> {
          return Single.just(accountWithAllProvidersResponse);
        }
        case "2" -> {
          return Single.just(accountWithAllProvidersResponseMultipleActive);
        }
        case "3" -> {
          return Single.just(emptyAccountsResponse);
        }
      }
    }

    // Default response when orgId is not set or doesn't match expected values
    return Single.just(accountWithAllProvidersResponse);
  }
}
