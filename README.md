# soap2rest

And example project fronting a REST service with a SOAP service.

## Requirements

- [Apache Maven 3.x](http://maven.apache.org)
- [MySQL 5.7.18](https://www.mysql.com/oem/)
  - [Docker Image](https://hub.docker.com/r/mysql/mysql-server/)

## Preparing

Install and run MySQL [https://dev.mysql.com/doc/refman/5.7/en/installing.html]

_Note: For my tests, I chose to run the docker image [https://hub.docker.com/r/mysql/mysql-server/]. You can run it using the command `docker run --name mysql -e MYSQL_DATABASE=example -e MYSQL_ROOT_PASSWORD=Abcd1234 -e MYSQL_ROOT_HOST=172.17.0.1 -p 3306:3306 -d mysql/mysql-server:5.7`. You can then connect and run SQL statements using the command `docker exec -it mysql mysql -uroot -p`._

Build the project source code

```
$ cd $PROJECT_ROOT
$ mvn clean install
```

## Running the code

```
$ cd $PROJECT_ROOT
$ mvn spring-boot:run
```

## Testing the code

There are a few test pieces in the `src/test` directory.

- SoapUI project in `src/test/soapui`.
  - Has tests for the SOAP service, as well as the backend REST service (with mocks).
  - Nice for sending in single requests, but not for load testing.
- JMeter project in `src/test/jmeter`.
  - Has load test for the SOAP frontend.
- NodeJS app in `src/test/nodejs`.
  - Can be used as a high-performance, mock backend.
