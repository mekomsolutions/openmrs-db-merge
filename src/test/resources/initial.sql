SET FOREIGN_KEY_CHECKS=0;

INSERT INTO person (person_id,gender,birthdate,birthdate_estimated,dead,death_date,deathdate_estimated,birthtime,cause_of_death,creator,date_created,changed_by,date_changed,voided,voided_by,date_voided,void_reason,uuid)
VALUES (1,'M',null,0,0,null,0,null,null,1,'2026-01-01 00:00:00',null,null,0, null, null, null,'1dbf30be-45a4-11f1-9ef0-0242ac140002');

INSERT INTO users (user_id,person_id,system_id,username,password,salt,secret_question,secret_answer,creator,date_created,changed_by,date_changed,retired,retired_by,date_retired,retire_reason,uuid)
VALUES (1,1,'admin',null,'admin-pass','admin-salt',null,null,1,'2026-01-01 00:00:00',null,null,0,null,null,null,'2011621d-45a5-11f1-9ef0-0242ac140002');

SET FOREIGN_KEY_CHECKS=1;
