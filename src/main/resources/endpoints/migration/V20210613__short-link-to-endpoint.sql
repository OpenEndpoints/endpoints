CREATE TABLE short_link_to_endpoint (
  short_link_to_endpoint_code  VARCHAR                    NOT NULL PRIMARY KEY,
  application                  VARCHAR                    NOT NULL,
  environment                  VARCHAR                    NOT NULL,
  endpoint                     VARCHAR                    NOT NULL,
  created_on                   TIMESTAMP WITH TIME ZONE   NOT NULL,
  CONSTRAINT short_link_application
    FOREIGN KEY (application, environment)
    REFERENCES application_publish (application_name, environment)
);

CREATE TABLE short_link_to_endpoint_parameter (
  short_link_to_endpoint_code  VARCHAR                    NOT NULL REFERENCES short_link_to_endpoint,
  parameter_name               VARCHAR                    NOT NULL,
  parameter_value              VARCHAR                    NOT NULL,
  PRIMARY KEY (short_link_to_endpoint_code, parameter_name)
);