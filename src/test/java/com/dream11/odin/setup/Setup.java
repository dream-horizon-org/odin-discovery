package com.dream11.odin.setup;

import static com.dream11.odin.oam.MockOAMProviderAccountService.*;
import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.AttachRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.CreateRoleRequest;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53ClientBuilder;
import com.amazonaws.services.route53.model.CreateHostedZoneRequest;
import com.amazonaws.services.route53.model.CreateHostedZoneResult;
import com.amazonaws.services.route53.model.HostedZoneConfig;
import com.dream11.odin.constant.TestConstants;
import com.dream11.odin.grpc.provideraccount.v1.Rx3ProviderAccountServiceGrpc;
import com.dream11.odin.injector.GuiceInjector;
import com.dream11.odin.oam.MockOAMProviderAccountService;
import com.dream11.odin.oam.interceptor.OrgIdServerInterceptor;
import com.dream11.odin.util.SharedDataUtil;
import com.google.inject.Guice;
import io.grpc.ServerInterceptors;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import io.vertx.rxjava3.core.Vertx;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

@Slf4j
public class Setup
    implements BeforeAllCallback, AfterAllCallback, ExtensionContext.Store.CloseableResource {

  static boolean started = false;

  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
  final Vertx vertx = Vertx.vertx();
  MySQLContainer<?> mySQLContainer;

  ExtensionContext extensionContext;

  private static final String roleName = "testAdminRole";
  private static final String assumeRolePolicyDocument =
      """
    {
      "Version": "2012-10-17",
      "Statement": [
        {
          "Effect": "Allow",
          "Principal": {"Service": "ec2.amazonaws.com"},
          "Action": "sts:AssumeRole"
        }
      ]
    }
    """;
  private static final String policyArn = "arn:aws:iam::aws:policy/AdministratorAccess";
  public static String localStackEndpoint = "http://localhost:";
  public static String region = "us-east-1";

  @Override
  public void afterAll(ExtensionContext extensionContext) {}

  private static AmazonRoute53 createRoute53Client() {
    return AmazonRoute53ClientBuilder.standard()
        .withEndpointConfiguration(
            new AwsClientBuilder.EndpointConfiguration(localStackEndpoint, region))
        .build();
  }

  private static String createHostedZone(
      AmazonRoute53 route53Client, String domainName, String description) {
    CreateHostedZoneRequest request =
        new CreateHostedZoneRequest()
            .withName(domainName)
            .withCallerReference(String.valueOf(System.currentTimeMillis())) // Unique reference
            .withHostedZoneConfig(new HostedZoneConfig().withComment(description));
    CreateHostedZoneResult result = route53Client.createHostedZone(request);
    return result.getHostedZone().getId();
  }

  @Override
  public void beforeAll(ExtensionContext extensionContext)
      throws IOException, SQLException, LiquibaseException {
    if (!started) {
      // Set AWS credentials as both system properties and environment variables
      System.setProperty("AWS_ACCESS_KEY_ID", "test");
      System.setProperty("AWS_SECRET_ACCESS_KEY", "test");
      System.setProperty("AWS_DEFAULT_REGION", "us-east-1");

      // Also set as environment variables for AWS SDK
      System.setProperty("aws.accessKeyId", "test");
      System.setProperty("aws.secretKey", "test");
      System.setProperty("aws.region", "us-east-1");
      DockerImageName localstackImage = DockerImageName.parse("localstack/localstack:1.3.0");
      LocalStackContainer localStackContainer =
          new LocalStackContainer(localstackImage)
              .withServices(
                  LocalStackContainer.Service.ROUTE53,
                  LocalStackContainer.Service.IAM,
                  LocalStackContainer.Service.STS)
              .withCreateContainerCmdModifier(cmd -> cmd.withPlatform("linux/amd64"));
      localStackContainer.start();
      String localStackPort = localStackContainer.getFirstMappedPort().toString();
      localStackEndpoint = "http://localhost:" + localStackPort;
      System.setProperty("awsEndpointPort", localStackPort);

      // Set up the IAM client to use LocalStack
      AmazonIdentityManagement iamClient =
          AmazonIdentityManagementClientBuilder.standard()
              .withEndpointConfiguration(
                  new AwsClientBuilder.EndpointConfiguration(localStackEndpoint, region))
              .build();

      // Create the IAM role with admin rights
      CreateRoleRequest createRoleRequest =
          new CreateRoleRequest()
              .withRoleName(roleName)
              .withAssumeRolePolicyDocument(assumeRolePolicyDocument);
      iamClient.createRole(createRoleRequest);

      AttachRolePolicyRequest attachRolePolicyRequest =
          new AttachRolePolicyRequest().withRoleName(roleName).withPolicyArn(policyArn);
      iamClient.attachRolePolicy(attachRolePolicyRequest);
      AmazonRoute53 route53Client = createRoute53Client();

      // Create example.local hosted zone
      String exampleLocalZoneId =
          createHostedZone(
              route53Client, "example.local", "A description for example.local hosted zone.");
      log.info("Endpoint for localstack " + localStackEndpoint);
      log.info("Hosted Zone ID for example.local: " + exampleLocalZoneId);
      System.setProperty("PRIVATE_HOSTED_ZONE", exampleLocalZoneId);

      // Create example.com hosted zone
      String exampleComZoneId =
          createHostedZone(
              route53Client, "example.com", "A description for example.com hosted zone.");
      log.info("Hosted Zone ID for example.com: " + exampleComZoneId);
      System.setProperty("PUBLIC_HOSTED_ZONE", exampleComZoneId);
      buildAWSr53Response(exampleLocalZoneId, exampleComZoneId);
      buildAWSr53ResponseNoActive(exampleLocalZoneId, exampleComZoneId);
      buildAWSr53ResponseMultipleActive(exampleLocalZoneId, exampleComZoneId);
      buildAccountWithAllProvidersResponse(exampleLocalZoneId, exampleComZoneId);

      this.mySQLContainer =
          new MySQLContainer<>(
                  System.getProperty(
                      TestConstants.MYSQL_IMAGE_KEY, TestConstants.DEFAULT_MYSQL_IMAGE))
              .withDatabaseName(TestConstants.MYSQL_DATABASE)
              .withUsername(TestConstants.MYSQL_USER)
              .withPassword(TestConstants.MYSQL_PASSWORD)
              .withCreateContainerCmdModifier(cmd -> cmd.withPlatform("linux/amd64"));

      mySQLContainer.start();
      log.info("Started mysql container on port:{}", mySQLContainer.getFirstMappedPort());

      String serverName = InProcessServerBuilder.generateName();
      grpcCleanup.register(
          InProcessServerBuilder.forName(serverName)
              .directExecutor()
              .addService(
                  ServerInterceptors.intercept(
                      new MockOAMProviderAccountService(), new OrgIdServerInterceptor()))
              .build()
              .start());
      var baseChannel =
          grpcCleanup.register(
              InProcessChannelBuilder.forName(serverName).directExecutor().build());
      Rx3ProviderAccountServiceGrpc.RxProviderAccountServiceStub oamClient =
          Rx3ProviderAccountServiceGrpc.newRxStub(baseChannel);
      extensionContext.getRoot().getStore(GLOBAL).put("oam", oamClient);

      this.setMysqlSystemProperties(mySQLContainer);

      this.runDatabaseMigrations();

      this.extensionContext = extensionContext;
      this.startApplication();
      started = true;
      extensionContext.getRoot().getStore(GLOBAL).put("test", this);
    }
  }

  @Override
  public void close() {
    log.info("Closing all resources");
    this.mySQLContainer.close();
    this.vertx.close();
  }

  private void startApplication() {
    log.info("Starting application for running integration tests");
    GuiceInjector injector =
        new GuiceInjector(
            Guice.createInjector(List.of(new TestModule(vertx.getDelegate(), extensionContext))));
    SharedDataUtil.setInstance(vertx.getDelegate(), injector);
    this.vertx
        .rxDeployVerticle(TestConstants.VERTICLE_NAME)
        .doOnError(
            error ->
                log.error("Error in deploying verticle : {}", TestConstants.VERTICLE_NAME, error))
        .doOnSuccess(v -> log.info("Deployed verticle : {}", TestConstants.VERTICLE_NAME))
        .blockingGet();
  }

  private void setMysqlSystemProperties(MySQLContainer<?> mySQLContainer) {
    for (String PREFIX : TestConstants.MYSQL_PREFIXES) {
      System.setProperty(PREFIX + TestConstants.MYSQL_HOST_KEY, mySQLContainer.getHost());
      System.setProperty(
          PREFIX + TestConstants.MYSQL_PORT_KEY,
          String.valueOf(mySQLContainer.getFirstMappedPort()));
      System.setProperty(
          PREFIX + TestConstants.MYSQL_DATABASE_KEY, mySQLContainer.getDatabaseName());
      System.setProperty(PREFIX + TestConstants.MYSQL_USER_KEY, mySQLContainer.getUsername());
      System.setProperty(PREFIX + TestConstants.MYSQL_PASSWORD_KEY, mySQLContainer.getPassword());
    }
    System.setProperty(TestConstants.APP_ENVIRONMENT, "test");
  }

  private void runDatabaseMigrations() throws LiquibaseException, SQLException {
    log.info("Starting migrations on data sources");
    String dbUrl =
        String.format(
            "jdbc:mysql://%s:%d/%s",
            mySQLContainer.getHost(),
            mySQLContainer.getFirstMappedPort(),
            TestConstants.MYSQL_DATABASE);
    try (Connection conn =
        DriverManager.getConnection(
            dbUrl, mySQLContainer.getUsername(), mySQLContainer.getPassword())) {
      Database database =
          DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));

      try (Liquibase liquibase =
          new Liquibase(
              "db/mysql/db-migrate-changelog.xml", new ClassLoaderResourceAccessor(), database)) {
        liquibase.update((String) null);
      }
    }
  }
}
