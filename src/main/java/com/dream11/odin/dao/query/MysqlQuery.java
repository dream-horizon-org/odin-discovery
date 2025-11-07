package com.dream11.odin.dao.query;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MysqlQuery {

  public static final String CREATE_RECORD =
      "INSERT INTO record(name, "
          + "ttl_in_seconds, weight, client_type, provider_id, type,identifier) VALUES (?,?,?,?,?,?,?);";

  public static final String CREATE_RECORD_DESTINATION =
      "INSERT INTO record_destination(record_id, " + "destination) VALUES (?,?);";

  public static final String CREATE_PROVIDER =
      "INSERT INTO provider(org_id, account_name, name, config,config_hash)"
          + " VALUES (?,?,?,?,?);";
  public static final String GET_PROVIDER_BY_ID =
      "SELECT provider.id, provider.name, provider.config, provider.account_name, provider.org_id,provider.config_hash"
          + " FROM provider WHERE provider.id = ? ;";

  public static final String GET_PROVIDER =
      "SELECT provider.id, provider.name, provider.config, provider.account_name, provider.org_id,provider.config_hash"
          + " FROM provider WHERE provider.name = ? AND provider.is_active=true AND provider.org_id = ? AND provider.account_name = ? and provider.config = ?;";
  public static final String GET_RECORD =
      "SELECT r.id, r.name, r.type, r.ttl_in_seconds, r.identifier, r.weight, r.client_type "
          + "FROM record r WHERE r.name = ? AND r.provider_id= ?;";
  public static final String GET_RECORD_WITH_IDENTIFIER =
      "SELECT r.id, r.name, r.type, r.ttl_in_seconds, r.identifier,rd.destination, r.weight, r.client_type "
          + "FROM record r JOIN record_destination rd on r.id=rd.record_id "
          + "WHERE r.name = ? AND r.provider_id= ? AND (r.identifier IS NULL OR r.identifier = ?);";

  public static final String DELETE_RECORD = "DELETE FROM record WHERE id=?;";

  public static final String DELETE_DANGLING_RECORD =
      "DELETE record from record "
          + "left join record_destination on record.id  = record_destination.record_id where record_destination.id is null and record.id = ?;";

  public static final String DELETE_RECORD_DESTINATION =
      "DELETE FROM record_destination WHERE record_id=?;";

  public static final String DELETE_RECORD_DESTINATION_BY_VALUE =
      "DELETE FROM record_destination WHERE record_id=? and destination=?;";
  public static final String UPDATE_RECORD =
      "UPDATE record SET ttl_in_seconds = ?, weight = ? WHERE id = ?;";
}
