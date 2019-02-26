## Coster.io - Expense service

Microservice responsible for listing, saving, editing and deleting expense items. Developed in spring-boot.

### Build the app:
* Prerequisites: Maven, JDK11 (note: JDK8 not yet tested)
* `mvn clean install` - if you have docker engine
* `mvn clean install -DskipDocker` - if not

### Run the app:
- Run: `mvn spring-boot:run -Dspring.profiles.active=dev`
    - the dev profile uses an in-memory database. If you have postgreSQL db running locally on the default port you might decide to leave out this profile flag
    - default port: 9000
    
### REST Interface:
- Swagger UI: localhost:9000/swagger-ui.html

### Actuator endpoints:
- Health: localhost:9000/actuator/health
- Beans: localhost:9000/actuator/beans
- Env vars: localhost:9000/actuator/env
- Status: localhost:9000/actuator/status
