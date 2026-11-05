CREATE TABLE reactions (emoji TEXT PRIMARY KEY, count INTEGER NOT NULL DEFAULT 0);
INSERT INTO reactions (emoji) VALUES ('fire'), ('cry'), ('skull');
CREATE TABLE taps (key TEXT PRIMARY KEY, day INTEGER NOT NULL);
CREATE INDEX taps_day ON taps (day);
