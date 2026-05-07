INSERT INTO person (person_id,gender,birthdate,birthdate_estimated,dead,death_date,deathdate_estimated,birthtime,cause_of_death,creator,date_created,changed_by,date_changed,voided,voided_by,date_voided,void_reason,uuid)
VALUES (101,'M','2000-01-01',1,1,'2020-01-01',1,'11:11',1,11,'2026-01-02 00:01:00',12,'2026-01-02 00:30:00',1,11,'2026-01-02 01:00:00','Testing','12ce70be-45a4-11f1-9ef0-0242ac140009'),
       (102,'F',null,0,0,null,0,null,null,12,'2026-01-02 00:02:00',null,null,0,null,null,null,'22ce70be-45a4-11f1-9ef0-0242ac140009'),
       (103,'M',null,0,0,null,0,null,null,13,'2026-01-02 00:03:00',null,null,0,null,null,null,'32ce70be-45a4-11f1-9ef0-0242ac140009'),
       (104,'F',null,0,0,null,0,null,null,14,'2026-01-02 00:04:00',null,null,0,null,null,null,'42ce70be-45a4-11f1-9ef0-0242ac140009'),
       (105,'M',null,0,0,null,0,null,null,15,'2026-01-02 00:05:00',null,null,0,null,null,null,'52ce70be-45a4-11f1-9ef0-0242ac140009');

INSERT INTO patient (patient_id,creator,date_created,changed_by,date_changed,voided,voided_by,date_voided,void_reason,allergy_status)
VALUES (101,11,'2026-01-02 00:01:00',12,'2026-01-02 00:30:00',1,11,'2026-01-02 01:00:00','Testing','Unknown'),
       (102,12,'2026-01-02 00:02:00',null,null,0,null,null,null,'No known allergies'),
       (103,13,'2026-01-02 00:03:00',null,null,0,null,null,null,'Unknown'),
       (104,14,'2026-01-02 00:04:00',null,null,0,null,null,null,'No known allergies'),
       (105,15,'2026-01-02 00:05:00',null,null,0,null,null,null,'Unknown');
