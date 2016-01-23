# morecat

Blog APIs

## Technologies

* Java 8
* Java EE 7
* [WildFly Swarm](http://wildfly-swarm.io/)

## APIs

// TODO Swagger

### Entry(Public)

#### GET all published(pageable)

``` sh
curl localhost:8080/entries
```

#### GET a published

``` sh
curl localhost:8080/entries/:yyyy/:MM/:dd/:permalink
```

### Entry(Admin)

#### GET

#### POST

``` sh
$ curl -X POST -H "Content-Type: application/json" -d '{"title":"title1", "permalink":"permalink1", "content":"content1","state":"PUBLIC", "format":"MARKDOWN"}' localhost:8080/admin/entries -v
```
#### PUT

``` sh
$ curl -X PUT -H "Content-Type: application/json" -d '{"title":"updated-title", "content":"updated-content", "permalink":"updated-permalink", "state":"PUBLIC", "format":"HTML"}'  localhost:8080/admin/entries/1 -v
```

#### DELETE

``` sh
$ curl -X DELETE  localhost:8080/admin/entries/1 -v
```

### Media

#### GET all(pageable)

``` sh
$ curl -X GET http://localhost:8080/media
```

#### GET

``` sh
$ curl -X GET http://localhost:8080/media/:uuid/:filename
```

### Media(Admin)

#### POST

``` sh
$ curl -X POST -F 'file=@some-media.jpg' http://localhost:8080/admin/media
```

#### DELETE

``` sh
$ curl -X DELETE http://localhost:8080/admin/media/:uuid/:filename
```

### Configuration

#### GET

``` sh
$ curl -X GET localhost:8080/configurations
```

### Configuration(Admin)

#### PUT

``` sh
$ curl -X PUT -H "Content-Type: application/json" -d '{"blogName" : "updated blog name", "blogDescription" : "updated blog description", "publicity" : true}' localhost:8080/admin/configurations -v
```

## Deployment

Put `.env` file under `docker` dir with copying from `.env.template` and fill out `POSTGRES_USER`, `POSTGRES_PASSWORD`.

Install [Docker Compose](https://docs.docker.com/compose/) and type the following command.

``` sh
docker-compose -f docker/docker-compose.yml up -d
```

## Build

``` sh
mvn clean package
```

### Build Docker Image

``` sh
mvn clean package docker:build
```

## Setup Environment

### Run DB(PostgreSQL)

``` sh
docker run -it -d \
  --name morecat-db \
  -e POSTGRES_USER=morecat -e POSTGRES_PASSWORD=morecat \
  -v /tmp/pgdata/data:/var/lib/postgresql/data \
  -p 5432:5432 \
  emag/morecat-db:1.0.0
```

#### Connect to DB by using `psql`

``` sh
docker run -it --rm \
  --link morecat-db:db \
  emag/morecat-db:1.0.0 \
  sh -c 'exec psql -h "$DB_PORT_5432_TCP_ADDR" -p "$DB_PORT_5432_TCP_PORT" -U morecat'
```

## Run morecat

``` sh
java \
  -Dswarm.morecat.db.host=localhost -Dswarm.morecat.db.port=5432 \
  -Dswarm.morecat.db.user=morecat -Dswarm.morecat.db.password=morecat \
  -jar target/morecat-swarm.jar
```
