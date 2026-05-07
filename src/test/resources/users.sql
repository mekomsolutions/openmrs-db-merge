INSERT INTO person (person_id,gender,birthdate,birthdate_estimated,dead,death_date,deathdate_estimated,birthtime,cause_of_death,creator,date_created,changed_by,date_changed,voided,voided_by,date_voided,void_reason,uuid)
VALUES (11,'M',null,0,0,null,0,null,null,1,'2026-01-01 00:01:00',null,null,0,null,null,null,'a1bf30be-45a4-11f1-9ef0-0242ac140009'),
       (12,'F','2000-01-02',0,0,'2020-01-02',0,'01:02',1,1,'2026-01-01 00:02:00',1,'2026-01-01 00:01:00',1,1,'2026-01-01 00:02:00','Testing','b1bf30be-45a4-11f1-9ef0-0242ac140009'),
       (13,'M',null,0,0,null,0,null,null,1,'2026-01-01 00:03:00',null,null,0,null,null,null,'c1bf30be-45a4-11f1-9ef0-0242ac140009'),
       (14,'F',null,0,0,null,0,null,null,1,'2026-01-01 00:04:00',null,null,0,null,null,null,'d1bf30be-45a4-11f1-9ef0-0242ac140009'),
       (15,'M',null,0,0,null,0,null,null,1,'2026-01-01 00:05:00',null,null,0,null,null,null,'e1bf30be-45a4-11f1-9ef0-0242ac140009');

INSERT INTO users (user_id,person_id,system_id,username,password,salt,secret_question,secret_answer,creator,date_created,changed_by,date_changed,retired,retired_by,date_retired,retire_reason,uuid)
VALUES (11,11,'test-11','test-user-11','test-pass-11','test-salt-11',null,null,1,'2026-01-01 00:01:00',null,null,0,null,null,null,'1091621d-45a5-11f1-9ef0-0242ac140008'),
       (12,12,'test-12','test-user-12','test-pass-12','test-salt-12',null,null,1,'2026-01-01 00:02:00',null,null,0,null,null,null,'2091621d-45a5-11f1-9ef0-0242ac140008'),
       (13,13,'test-13','test-user-13','test-pass-13','test-salt-13',null,null,1,'2026-01-01 00:03:00',null,null,0,null,null,null,'3091621d-45a5-11f1-9ef0-0242ac140008'),
       (14,14,'test-14','test-user-14','test-pass-14','test-salt-14',null,null,1,'2026-01-01 00:04:00',null,null,0,null,null,null,'4091621d-45a5-11f1-9ef0-0242ac140008'),
       (15,15,'test-15','test-user-15','test-pass-15','test-salt-15',null,null,1,'2026-01-01 00:05:00',null,null,0,null,null,null,'5091621d-45a5-11f1-9ef0-0242ac140008');
