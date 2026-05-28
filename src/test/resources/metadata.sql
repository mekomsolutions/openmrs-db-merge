INSERT INTO concept_datatype (concept_datatype_id,name,hl7_abbreviation,creator,date_created,retired,uuid)
VALUES (1,'Coded','CWE',1,'2026-01-01 00:00:00',0,'167015d7-496b-11f1-b548-0242ac140002'),
       (2,'N/A','ZZ',1,'2026-01-01 00:00:00',0,'267015d7-496b-11f1-b548-0242ac140002'),
       (3,'NUMERIC','NM',1,'2026-01-01 00:00:00',0,'367015d7-496b-11f1-b548-0242ac140002');

INSERT INTO concept_class (concept_class_id,name,creator,date_created,retired,uuid)
VALUES (1,'Question',1,'2026-01-01 00:00:00',0,'1df33f36-496b-11f1-b548-0242ac140002'),
       (2,'Misc',1,'2026-01-01 00:00:00',0,'2df33f36-496b-11f1-b548-0242ac140002');

INSERT INTO concept (concept_id,datatype_id,class_id,is_set,creator,date_created,retired,uuid)
VALUES (1,1,1,0,1,'2026-01-01 00:00:00',0,'14976266-496c-11f1-b548-0242ac140002'),
       (2,2,2,0,1,'2026-01-01 00:00:00',0,'24976266-496c-11f1-b548-0242ac140002'),
       (3,3,1,0,1,'2026-01-01 00:00:00',0,'34976266-496c-11f1-b548-0242ac140002');

INSERT INTO concept_name (concept_name_id,concept_id,name,locale,creator,date_created,voided,uuid)
VALUES (1,1,'Cause Of Death','en',1,'2026-01-01 00:00:00',0,'15876266-496c-11f1-b548-0242ac140004'),
       (2,2,'Other','en',1,'2026-01-01 00:00:00',0,'25876266-496c-11f1-b548-0242ac140004'),
       (3,3,'Weight','en',1,'2026-01-01 00:00:00',0,'35876266-496c-11f1-b548-0242ac140004');

INSERT INTO visit_type (visit_type_id,name,creator,date_created,retired,uuid)
VALUES (1,'Adult Initial',1,'2026-01-01 00:00:00',0,'877015d7-496b-11f1-b548-0242ac140002');

INSERT INTO location (location_id,name,creator,date_created,retired,uuid)
VALUES (1,'Inpatient Clinic',1,'2026-01-01 00:00:00',0,'567015d7-496b-11f1-b548-0242ac140002');

INSERT INTO encounter_type (encounter_type_id,name,creator,date_created,retired,uuid)
VALUES (1,'Consultation',1,'2026-01-01 00:00:00',0,'346015d7-496b-11f1-b548-0242ac140002');

INSERT INTO form (form_id,name,version,published,creator,date_created,retired,uuid)
VALUES (1,'Triage','v1',1,1,'2026-01-01 00:00:00',0,'318015d7-496b-11f1-b548-0242ac140002');

INSERT INTO role (role,uuid)
VALUES ('Doctor','156015d7-496b-11f1-b548-0242ac140003'),
       ('Nurse','256015d7-496b-11f1-b548-0242ac140003'),
       ('Registration Clerk','356015d7-496b-11f1-b548-0242ac140003'),
       ('Data Clerk','456015d7-496b-11f1-b548-0242ac140003'),
       ('Pharmacist','556015d7-496b-11f1-b548-0242ac140003');

