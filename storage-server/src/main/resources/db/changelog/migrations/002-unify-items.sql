--liquibase formatted sql

--changeset jakub:002-unify-items

CREATE TABLE item
(
    id           UUID PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    parent_id    UUID REFERENCES item (id)
        ON DELETE RESTRICT,
    type         VARCHAR(16)  NOT NULL,
    size         BIGINT       NOT NULL DEFAULT 0,
    content_type VARCHAR(255),
    checksum     VARCHAR(64),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    metadata_id  UUID         NOT NULL UNIQUE
        REFERENCES metadata (id),

    CONSTRAINT chk_item_name
        CHECK (length(trim(name)) > 0),

    CONSTRAINT chk_item_type
        CHECK (type IN ('FILE', 'DIRECTORY')),

    CONSTRAINT chk_item_not_self_parent
        CHECK (parent_id IS DISTINCT FROM id),

    CONSTRAINT chk_item_size
        CHECK (size >= 0),

    CONSTRAINT chk_item_checksum
        CHECK (
            checksum IS NULL
            OR checksum ~ '^[a-fA-F0-9]{64}$'
        ),

    CONSTRAINT chk_item_type_attributes
        CHECK (
            (type = 'FILE' AND checksum IS NOT NULL)
            OR
            (type = 'DIRECTORY'
                AND checksum IS NULL
                AND content_type IS NULL)
        )
);

-- Unique names within the same parent directory,
-- including the virtual root.

CREATE UNIQUE INDEX uq_item_parent_name
    ON item (parent_id, name) NULLS NOT DISTINCT;

-- Item type filtering.

CREATE INDEX idx_item_type
    ON item (type);

-- Migrate existing directories.

INSERT INTO item (
    id,
    name,
    parent_id,
    type,
    size,
    content_type,
    checksum,
    created_at,
    updated_at,
    metadata_id
)
SELECT
    id,
    name,
    parent_id,
    'DIRECTORY',
    0,
    NULL,
    NULL,
    created_at,
    updated_at,
    metadata_id
FROM directory;

-- Migrate existing files.

INSERT INTO item (
    id,
    name,
    parent_id,
    type,
    size,
    content_type,
    checksum,
    created_at,
    updated_at,
    metadata_id
)
SELECT
    id,
    name,
    directory_id,
    'FILE',
    size,
    content_type,
    checksum,
    created_at,
    updated_at,
    metadata_id
FROM file;

-- Calculate initial directory sizes.

WITH RECURSIVE item_sizes AS (
    SELECT parent_id, size
FROM item
WHERE type = 'FILE'
  AND parent_id IS NOT NULL

UNION ALL

SELECT i.parent_id, s.size
FROM item_sizes s
         JOIN item i ON i.id = s.parent_id
WHERE i.parent_id IS NOT NULL
    ),
directory_sizes AS (
    SELECT parent_id AS id, SUM(size) AS size
    FROM item_sizes
    GROUP BY parent_id
)
UPDATE item i
SET size = s.size
    FROM directory_sizes s
WHERE i.id = s.id
  AND i.type = 'DIRECTORY';

-- Remove obsolete tables.

DROP TABLE file;
DROP TABLE directory;