# morecat-api

## APIs

## Deployment

## Development

### Build

``` sh
mvn clean package
```

### Setup Environment

#### 1. Run DB(PostgreSQL)

``` sh
docker run -it -d \
  --name morecat-db \
  -e POSTGRES_USER=morecat -e POSTGRES_PASSWORD=morecat \
  -v /tmp/pgdata/data:/var/lib/postgresql/data \
  -p 5432:5432 \
  emag/morecat-db:1.0.0
```

### Run MoreCat API Server

``` sh
java \
  -Dmorecat.db.host=localhost -Dmorecat.db.port=5432 -Dmorecat.db.user=morecat -Dmorecat.db.password=morecat \
  -jar morecat-api-swarm.jar
```

### Connect to DB by using `psql`

``` sh
docker run -it --rm \
  --link morecat-db:db \
  emag/morecat-db:1.0.0 \
  sh -c 'exec psql -h "$DB_PORT_5432_TCP_ADDR" -p "$DB_PORT_5432_TCP_PORT" -U morecat'
```
