# openFHIR

openFHIR is an engine that implements [FHIR Connect specification](https://github.com/better-care/fhir-connect-mapping-spec) and facilitates bidirectional mappings between openEHR and FHIR.

## [Documentataion](https://open-fhir.com/documentation/index.html)

## Build
### With docker and docker-compose

Build the docker image
```
docker-compose build
```

Run all services
```
docker-compose up
```

### Build with Maven
Build the jar
```
mvn clean package
```

Setup postgres (skip if postgres is locally configured)
```
docker-compose up db
```

Run using
```
java -jar target/*.jar
```

## Test using the REST API
1. Download and setup the Postman collection [here](https://github.com/medblocks/openFHIR/blob/main/src/test/resources/kds/KDS.postman_collection.json)

2. Set variable `{{baseUrl}}` to `http://localhost:8080`

3. Run [EHRBase](github.com/ehrbase/ehrbase) on `http://localhost:8081` to test if compositions are being saved.

4. Test the requests in the collection!