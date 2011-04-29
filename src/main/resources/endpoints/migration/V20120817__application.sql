CREATE TABLE application (
  application                  VARCHAR      NOT NULL,
  directory_suffix             VARCHAR      NOT NULL,
  svn_revision                 INT          NOT NULL,
  CONSTRAINT pk_application PRIMARY KEY (application)
);
