WITH new_metadata AS (
INSERT INTO metadata (id)
VALUES (gen_random_uuid())
    RETURNING id)
INSERT
INTO item (id, name, parent_id, type, metadata_id)
SELECT gen_random_uuid(),
       'test dir 2',
       NULL,
       'DIRECTORY',
       id
FROM new_metadata;

select * from metadata;
select * from item;