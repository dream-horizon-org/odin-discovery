package com.dream11.odin.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dream11.odin.constant.TestConstants;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.vertx.core.json.JsonObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@Slf4j
@UtilityClass
public class TestUtil {

  public void assertRecordCreatedInDB(String recordName, List<String> values) throws SQLException {
    String query =
        "SELECT r.name, rd.destination from record r JOIN record_destination rd on r.id=rd.record_id where r.name = '"
            + recordName
            + "'";
    try (PreparedStatement preparedStatement = getDatabaseConnection().prepareStatement(query)) {
      ResultSet resultSet = preparedStatement.executeQuery();
      assertTrue(resultSet.next());
      Set<String> destinations = new HashSet<>();
      do {
        destinations.add(resultSet.getString("destination"));

      } while (resultSet.next());
      assertEquals(new HashSet<>(values), destinations);
    }
  }

  public void assertRecordDeletedFromDB(String recordName) throws SQLException {
    String query =
        "SELECT r.name, rd.destination from record r LEFT JOIN record_destination rd on r.id=rd.record_id where r.name = '"
            + recordName
            + "'";
    try (PreparedStatement preparedStatement = getDatabaseConnection().prepareStatement(query)) {
      ResultSet resultSet = preparedStatement.executeQuery();
      assertFalse(resultSet.next());
    }
  }

  public static final ObjectMapper OBJECT_MAPPER =
      JsonMapper.builder()
          .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
          .serializationInclusion(JsonInclude.Include.NON_NULL)
          .build();

  public Connection getDatabaseConnection() throws SQLException {
    String mysqlMasterHost = TestConstants.MYSQL_PREFIXES.get(0);
    String url =
        String.format(
            "jdbc:mysql://localhost:%s/%s?autoReconnect=true&allowMultiQueries=true",
            System.getProperty(mysqlMasterHost + TestConstants.MYSQL_PORT_KEY),
            System.getProperty(mysqlMasterHost + TestConstants.MYSQL_DATABASE_KEY));

    return DriverManager.getConnection(
        url,
        System.getProperty(mysqlMasterHost + TestConstants.MYSQL_USER_KEY),
        System.getProperty(mysqlMasterHost + TestConstants.MYSQL_PASSWORD_KEY));
  }

  @SneakyThrows
  public void executeSqlFile(Connection connection, String dirName, String fileName) {
    String filePath = String.format("%s/%s", dirName, fileName);
    String content = FileUtils.readFileToString(new File(filePath), Charset.defaultCharset());
    executeSqlStatement(connection, content);
  }

  @SneakyThrows
  public void executeSqlFromDir(Connection connection, String dirName) {

    File folder = new File(dirName);
    File[] listOfFiles = folder.listFiles();
    if (listOfFiles != null) {

      Arrays.stream(listOfFiles)
          .filter(File::isFile)
          .forEach(
              fileName -> {
                String content = null;
                try {
                  content =
                      FileUtils.readFileToString(
                          new File(fileName.getPath()), Charset.defaultCharset());
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
                executeSqlStatement(connection, content);
              });
    } else {
      throw new RuntimeException("The directory is empty or it does not exist: " + dirName);
    }
  }

  @SneakyThrows
  public void executeSqlStatement(Connection connection, String query) {
    try (Statement statement = connection.createStatement()) {
      // start a transaction
      connection.setAutoCommit(false);
      statement.executeUpdate(query);
      connection.commit();
    } catch (SQLException e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(true);
    }
  }

  public <T extends Message.Builder> T jsonToProtoBuilder(JsonObject json, T builder)
      throws InvalidProtocolBufferException {
    JsonFormat.parser().ignoringUnknownFields().merge(json.encode(), builder);
    return builder;
  }
}
