package com.dream11.odin.client;

import io.reactivex.rxjava3.core.Completable;
import io.vertx.rxjava3.mysqlclient.MySQLPool;

public interface MysqlClient {

  MySQLPool getMasterClient();

  MySQLPool getSlaveClient();

  Completable rxConnect();

  Completable rxClose();
}
