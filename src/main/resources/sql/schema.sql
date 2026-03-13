CREATE TABLE IF NOT EXISTS config_record (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uid TEXT NOT NULL,
    namespace TEXT NOT NULL,
    "key" TEXT NOT NULL,
    "value" TEXT,
    deleted INTEGER DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(namespace, "key")
);

-- Create an index for faster lookups by namespace and key
CREATE INDEX IF NOT EXISTS idx_config_namespace_key ON config_record (namespace, "key");

CREATE TABLE IF NOT EXISTS project_record (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uid TEXT NOT NULL,
    name TEXT NOT NULL,
    path TEXT NOT NULL,
    deleted INTEGER DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_project_uid ON project_record (uid);

CREATE TABLE IF NOT EXISTS conversation_record (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uid TEXT NOT NULL,
    project_uid TEXT NOT NULL,
    title TEXT,
    deleted INTEGER DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_conversation_uid ON conversation_record (uid);
CREATE INDEX IF NOT EXISTS idx_conversation_project_uid ON conversation_record (project_uid);

CREATE TABLE IF NOT EXISTS chat_message_record (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uid TEXT NOT NULL,
    conversation_uid TEXT NOT NULL,
    message_type TEXT,
    text TEXT,
    tool_calls TEXT,
    responses TEXT,
    metadata TEXT,
    deleted INTEGER DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_message_uid ON chat_message_record (uid);
CREATE INDEX IF NOT EXISTS idx_chat_message_conversation_uid ON chat_message_record (conversation_uid);
