# morecat-api

## APIs

## Deployment

## Development

### Build

### Setup Environment

#### 1. Run DB(PostgreSQL)

``` sh
docker run -it -d \
  --name morecat-db \
  -e POSTGRES_USER=morecat -e POSTGRES_PASSWORD=morecat \
  -v /tmp/pgdata/data:/var/lib/postgresql/data \
  emag/morecat-db:1.0.0
```

### Connect to DB by using `psql`

``` sh
docker run -it --rm \
  --link morecat-db:db \
  emag/morecat-db:1.0.0 \
  sh -c 'exec psql -h "$DB_PORT_5432_TCP_ADDR" -p "$DB_PORT_5432_TCP_PORT" -U morecat'
```
