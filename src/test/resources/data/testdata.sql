INSERT INTO provider(id, org_id, name, account_name, config, is_active,config_hash) VALUES (1, 1, 'Consul','consulAccount','{"host":"127.0.0.1","port":"8082","domains":[{"name":"example-stag.local","isActive":true}],"timeoutInSecs":3.0}',1,'10b4d26dd9ca88e6b6cd78287651ec294cae0e38444ce06d06294a804124a475');

INSERT INTO record(id, name, type, provider_id, ttl_in_seconds, weight, client_type, identifier)
VALUES (1, 'testRecordToDelete.example-stag.local', '', 1, 0, 0, 'CONTROLLER', '');

INSERT INTO record(id, name, type, provider_id, ttl_in_seconds, weight, client_type, identifier)
VALUES (2, 'singleRouteUpdate.example-stag.local', '', 1, 0, 0, 'CONTROLLER', '');

INSERT INTO record(id, name, type, provider_id, ttl_in_seconds, weight, client_type, identifier)
VALUES (3, 'singleRouteAppend.example-stag.local', '', 1, 0, 0, 'CONTROLLER', '');

INSERT INTO record(id, name, type, provider_id, ttl_in_seconds, weight, client_type, identifier)
VALUES (4, 'singleRouteValueRemoved.example-stag.local', '', 1, 0, 0, 'CONTROLLER', '');


INSERT INTO record_destination(id, record_id, destination)
VALUES (1, 1, '140.23.12.12');

INSERT INTO record_destination(id, record_id, destination)
VALUES (2, 2, '138.23.12.12');

INSERT INTO record_destination(id, record_id, destination)
VALUES (3, 3, '138.23.12.13');

INSERT INTO record_destination(id, record_id, destination)
VALUES (4, 4, '138.23.12.13');

INSERT INTO record_destination(id, record_id, destination)
VALUES (5, 4, '138.23.12.14');

