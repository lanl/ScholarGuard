CREATE DATABASE crawl;
 use crawl;
//CREATE TABLE crawl (  url VARCHAR(512),  status VARCHAR(16) DEFAULT 'DISCOVERED',  nextfetchdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,  metadata TEXT,  bucket SMALLINT DEFAULT 0,  PRIMARY KEY(url) );
//CREATE TABLE crawl (  url VARCHAR(512),  status VARCHAR(16) DEFAULT 'DISCOVERED',  nextfetchdate datetime  DEFAULT CURRENT_TIMESTAMP,  metadata TEXT,  bucket SMALLINT DEFAULT 0,  PRIMARY KEY(url) );

CREATE TABLE urls (
 url VARCHAR(512),
 status VARCHAR(16) DEFAULT 'DISCOVERED',
 nextfetchdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 metadata TEXT,
 bucket SMALLINT DEFAULT 0,
 host VARCHAR(128),
 id char(32)
 PRIMARY KEY(id)
);

ALTER TABLE urls ADD INDEX b (`bucket`);
ALTER TABLE urls ADD INDEX t (`nextfetchdate`);
ALTER TABLE urls ADD INDEX h (`host`);
ALTER TABLE urls ADD INDEX u (`url`);

GRANT ALL PRIVILEGES ON urls TO 'cache'@'localhost' IDENTIFIED BY  'plenty' WITH GRANT OPTION;
FLUSH PRIVILEGES;
//ALTER DATABASE crawl CHARACTER SET utf8 COLLATE utf8_unicode_ci;
//INSERT INTO crawl (url, metadata) VALUES ("https://twitter.com/azaroth42","isSitemap=false");
//INSERT INTO crawl (url, metadata) VALUES ("https://twitter.com/azaroth42",""); //?
