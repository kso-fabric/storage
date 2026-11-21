WITH new_metadata AS (
    INSERT INTO metadata (id)
        VALUES (gen_random_uuid())
        RETURNING id)
INSERT
INTO file (id, name, directory_id, size, checksum, metadata_id)
SELECT gen_random_uuid(),
       'test.txt',
       NULL,
       10,
       repeat('a', 64),
       id
FROM new_metadata;

select * from metadata;
select * from file;