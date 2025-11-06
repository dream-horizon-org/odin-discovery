package com.dream11.odin.provider;

import com.dream11.odin.domain.Record;
import com.dream11.odin.domain.RecordDiff;
import io.reactivex.rxjava3.core.Completable;

public interface DiscoveryProvider {

  Completable createRecord(Record dnsRecord);

  Completable updateRecord(Record dnsRecord, RecordDiff values);

  Completable deleteRecord(Record recordName);
}
