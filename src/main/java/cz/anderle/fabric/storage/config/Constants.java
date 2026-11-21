package cz.anderle.fabric.storage.config;

public class Constants {

    public static class Table {

        public static class Directory {
            public static final String TABLE_NAME = "directory";
            public static final String PARENT_ID = "parent_id";
        }

        public static class File {
            public static final String TABLE_NAME = "file";
            public static final String DIRECTORY_ID = "directory_id";
        }

        public static class Entry {
            public static final String NAME = "name";
            public static final String CREATED_AT = "created_at";
            public static final String UPDATED_AT = "updated_at";
            public static final String METADATA_ID = "metadata_id";
        }

        public static class Metadata {
            public static final String TABLE_NAME = "metadata";
            public static final String DESCRIPTION = "description";
            public static final String CUSTOM = "custom";
        }

        public static class Tag {
            public static final String TABLE_NAME = "tag";
            public static final String VALUE = "value";
        }

        public static class MetadataTag {
            public static final String TABLE_NAME = "metadata_tag";
            public static final String METADATA_ID = "metadata_id";
            public static final String TAG_ID = "tag_id";
        }
    }
}
