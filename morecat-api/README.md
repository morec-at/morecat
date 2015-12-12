# morecat-api

## APIs

// TODO Swagger

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

### Docker Build

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

## Run MoreCat API Server

``` sh
java \
  -Dswarm.morecat.db.host=localhost -Dswarm.morecat.db.port=5432 \
  -Dswarm.morecat.db.user=morecat -Dswarm.morecat.db.password=morecat \
  -jar target/morecat-api-swarm.jar
```
