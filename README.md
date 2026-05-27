# DB Importer
Imports data from a source database into another destination database

# Table of Contents

1. [About](#about)
2. [Build Requirements](#build-requirements)
3. [Run Requirements](#run-requirements)
4. [How To Build](#how-to-build)
5. [How To Run Application](#how-to-run-application)
6. [Configuration](#configuration)

## About
A spring boot application that merges 2 OpenMRS databases by copying all rows from tables in a source database to tables 
in a destination database.

The application does not sync rows in a table specified in [this exclusion file](src/main/resources/exclude_tables.txt).

## Build Requirements
- A unix operating system (Never been tested on windows)
- [Apache Maven 3.6.0](http://maven.apache.org/install.html)
- [OpenJDK 17](https://openjdk.java.net/install/)
- [Git](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git)

## Run Requirements
- A unix operating system (Never been tested on windows)
- Java 17+

## How To Build
Run the command below,
```
mvn clean install
```

## How To Run Application
**NOTE:** It is assumed the application is **NOT** run inside a docker container.

**NOTE:** Before running, the application you **MUST** first add a unique constraint to the `uuid` column in the `users` 
table in the destination database. And, you **MUST** also remove the constraint after the users tables is processed or 
the entire merge has completed. Below are the queries to add and remove the constraint.

Add constraint
```
ALTER TABLE openmrs.users ADD UNIQUE INDEX uuid_uk (uuid ASC);
```

Remove constraint
```
ALTER TABLE openmrs.users DROP INDEX uuid_uk;
```

```shell
java -jar db-importer-{VERSION}.jar
```

## Configuration

Below are the provided environment variables to configure the application.

| Name | Description | Required | Default Value |
|------|-------------|:--------:|:-------------:|
| SOURCE_DB_URL | The JDBC connection URL for the source database |Yes||
| SOURCE_DB_USERNAME | The database user for the source database | Yes ||
| SOURCE_DB_PASSWORD | The database password for the source database | Yes ||
| SINK_DB_URL | The JDBC connection URL for the destination database | Yes ||
| SINK_DB_USERNAME | The database user for the destination database | Yes ||
| SINK_DB_PASSWORD | The database password for the destination database | Yes ||
| MGT_DB_URL | The JDBC connection URL for the management database | Yes ||
| MGT_DB_USERNAME | The database user for the management database | Yes ||
| MGT_DB_PASSWORD | The database password for the management database | Yes ||
| READ_BATCH_SIZE | The number of rows to read from the source database for processing | No | 1000 |
| WRITE_BATCH_SIZE | The number of rows to write to the destination database in each call | No | 250 |
| THREAD_COUNT | The number of threads to use for parallel processing of rows | No | Twice the CPU cores |
| MAX_CONN_POOL_SIZE | The number of threads to use for parallel processing of rows, not that this value is applied to all the DB datasources i.e. source, destination and management, it is recommended to have this matching the value of `THREAD_COUNT` if the thread count is not above the allowed connection count by the database server | No | Twice the CPU cores |
| RETRY_FAILED_ITEMS | Specifies whether rows in the failure queue should be re-processed | No | false |
| LOG_CFG_FILE | The path to the file for the logback configuration file to use, defaults to one bundled with the application | No ||
