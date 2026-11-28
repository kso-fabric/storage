WITH new_metadata AS (
    INSERT INTO metadata (id)
        VALUES (gen_random_uuid())
        RETURNING id)
INSERT
INTO item (id, name, parent_id, type, size, checksum, metadata_id)
SELECT gen_random_uuid(),
       'test2.txt',
       NULL,
       'FILE',
       10,
       repeat('a', 64),
       id
FROM new_metadata;

select * from metadata;
select * from item;