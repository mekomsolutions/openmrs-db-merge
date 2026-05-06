SET FOREIGN_KEY_CHECKS=0;

INSERT INTO person (person_id,gender,birthdate_estimated,dead,deathdate_estimated,creator,date_created,voided,uuid)
VALUES (1,'M',0,0,0,1,'2026-01-01 00:00:00',0,'1dbf30be-45a4-11f1-9ef0-0242ac140005');

INSERT INTO users (user_id,person_id,system_id,username,password,salt,creator,date_created,retired,uuid)
VALUES (1,1,'admin',null,'admin-pass-sink','admin-salt-sink',1,'2026-01-01 00:00:00',0,'1cb1621d-45a5-11f1-9ef0-0242ac140006');

SET FOREIGN_KEY_CHECKS=1;

INSERT INTO users (user_id,person_id,system_id,username,password,salt,creator,date_created,retired,uuid)
VALUES (2,1,'daemon','daemon',null,null,1,'2026-01-01 00:00:00',0,'A4F30A1B-5EB9-11DF-A648-37A07F9C90FB');
