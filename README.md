# MoreCat

Blog APIs &amp; tools

## APIs

## Deployment

### morecat-api

### morecat-admin-web

## Development

### morecat-api

### Build

``` sh
./gradlew war -b morecat-api/build.gradle
```

### Setup Environment

#### 1. Run DB(PostgreSQL)

``` sh
docker run --name morecat-db \
-e POSTGRES_USER=morecat -e POSTGRES_PASSWORD=morecat \
-v /path/to/pgdata/data:/var/lib/postgresql/data \
-d emag/morecat-db
```
#### 2. Run WildFly & Deploy App

``` sh
docker run --link morecat-db:db \
-e DATASOURCE_NAME=morecatDS -e DB_NAME=morecat -e DB_USER=morecat \
-v /path/to/morecat/morecat-api/build/libs/:/opt/jboss/wildfly/standalone/deployments/:rw \
-p 8080:8080 \
--rm -it emag/morecat
```

If you'd like to use WildFly admin console, pass the administration information(`WILDFLY_ADMIN_USER`, `WILDFLY_ADMIN_PASSWORD`) and forwarding `9990`.

``` sh
docker run --link morecat-db:db \
-e DATASOURCE_NAME=morecatDS -e DB_NAME=morecat -e DB_USER=morecat \
-e WILDFLY_ADMIN_USER=admin -e WILDFLY_ADMIN_PASSWORD=Admin#70365 \
-v /path/to/morecat/morecat-api/build/libs/:/opt/jboss/wildfly/standalone/deployments/:rw \
-p 8080:8080 -p 9990:9990 \
--rm -it emag/morecat
```

### Manually Migrate DB Schema

You don't usually have to migrate your database schema by yourself because MoreCat has the automatic migration mechanism.

But for the convenience of checking the database state, you can use flyway plugin.

``` sh
./gradlew flywayInfo -b morecat-api/build.gradle -Ddatabase.url=jdbc:postgresql://`docker inspect --format="{{ .NetworkSettings.IPAddress }}" morecat-db`:5432/morecat -Ddatabase.user=morecat -Ddatabase.password=morecat
```

``` sh
./gradlew flywayMigrate -b morecat-api/build.gradle -Ddatabase.url=jdbc:postgresql://`docker inspect --format="{{ .NetworkSettings.IPAddress }}" morecat-db`:5432/morecat -Ddatabase.user=morecat -Ddatabase.password=morecat
```

``` sh
./gradlew flywayClean -b morecat-api/build.gradle -Ddatabase.url=jdbc:postgresql://`docker inspect --format="{{ .NetworkSettings.IPAddress }}" morecat-db`:5432/morecat -Ddatabase.user=morecat -Ddatabase.password=morecat
```

For more details, please refer to [the official documentation](http://flywaydb.org/documentation/gradle/).

### Connect to DB by using `psql`

``` sh
docker run --link morecat-db:db \
--rm -it emag/morecat-db \
sh -c 'exec psql -h "$DB_PORT_5432_TCP_ADDR" -p "$DB_PORT_5432_TCP_PORT" -U morecat'
```

### morecat-admin-web
