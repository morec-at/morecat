CREATE TABLE entries (
  id serial NOT NULL,
  version int4 NOT NULL,
  content text NOT NULL,
  created_date date NOT NULL,
  created_time time NOT NULL,
  format varchar(255) NOT NULL,
  permalink varchar(255) NOT NULL,
  state varchar(255) NOT NULL,
  title varchar(255) NOT NULL,
  author_name varchar(255) NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE configurations (
  id serial NOT NULL,
  version int4 NOT NULL,
  blog_description varchar(255) NOT NULL,
  blog_name varchar(255) NOT NULL,
  publicity boolean NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE tags (
  entry_id int4 NOT NULL,
  value varchar(255)
);

CREATE TABLE media (
  id SERIAL NOT NULL,
  version INT4 NOT NULL,
  content OID NOT NULL,
  name VARCHAR(255) NOT NULL,
  uuid VARCHAR(255) NOT NULL,
  author_name varchar(255) NOT NULL,
  created_time timestamp NOT NULL,
  PRIMARY KEY (id)
);

ALTER TABLE entries ADD CONSTRAINT UK_tb3g11m5y7xcicx8j00dbcw00  UNIQUE (permalink, created_date);
ALTER TABLE tags ADD CONSTRAINT FK_ap7xdwu7utpd2iysc1ots6wes FOREIGN KEY (entry_id) REFERENCES entries;
