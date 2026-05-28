INSERT INTO encounter (encounter_id,patient_id,encounter_type,encounter_datetime,visit_id,location_id,form_id,creator,date_created,changed_by,date_changed,voided,voided_by,date_voided,void_reason,uuid)
VALUES (1,101,1,'2026-01-02 01:01:00',1,1,1,11,'2026-01-02 01:01:00',12,'2026-01-02 02:31:00',1,11,'2026-01-02 02:45:00','testing','11224ab4-4a38-11f1-b426-0242ac140002'),
       (2,101,1,'2026-01-03 03:00:00',2,null,null,11,'2026-01-03 03:00:00',null,null,0,null,null,null,'21224ab4-4a38-11f1-b426-0242ac140002'),
       (3,102,1,'2026-01-02 01:03:00',null,null,null,11,'2026-01-02 01:03:00',null,null,0,null,null,null,'31224ab4-4a38-11f1-b426-0242ac140002'),
       (4,103,1,'2026-01-02 01:04:00',null,null,null,11,'2026-01-02 01:04:00',null,null,0,null,null,null,'41224ab4-4a38-11f1-b426-0242ac140002'),
       (5,104,1,'2026-01-02 01:05:00',null,null,null,11,'2026-01-02 01:05:00',null,null,0,null,null,null,'51224ab4-4a38-11f1-b426-0242ac140002');

