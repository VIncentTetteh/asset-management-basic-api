#!/bin/bash

# Asset Management System - Quick Setup Guide

## Prerequisites
- Java 21+
- Maven 3.8+
- PostgreSQL 12+

## Database Setup

```sql
-- Create database
CREATE DATABASE asset_management;

-- Create user
CREATE USER asset_user WITH PASSWORD 'your_secure_password';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE asset_management TO asset_user;
```

## Environment Setup

### 1. Create .env file in project root
```
SPRING_APPLICATION_NAME=Asset Management System
SERVER_PORT=8080
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/asset_management
SPRING_DATASOURCE_USERNAME=asset_user
SPRING_DATASOURCE_PASSWORD=your_secure_password
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.PostgreSQLDialect
SPRING_JPA_HIBERNATE_DDL_AUTO=update
JWT_SECRET=your-secret-key-min-32-chars-for-production
JWT_EXPIRATION=86400000
```

### 2. Update application.properties
Environment variables are automatically loaded from .env file via java-dotenv library.

## Build & Run

### Clean Build
```bash
mvn clean install -DskipTests
```

### Run Application
```bash
mvn spring-boot:run
```

### Run with JAR
```bash
mvn clean package -DskipTests
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

## Testing

### Run All Tests
```bash
mvn test
```

### Run Specific Test Class
```bash
mvn test -Dtest=AssetServiceTest
```

### Run with Coverage
```bash
mvn clean test jacoco:report
```

## API Documentation

Once running, access:
- **Health Check**: `http://localhost:8080/actuator/health`
- **Base API**: `http://localhost:8080/api/v1/`

## Sample API Calls

### Create Organization
```bash
curl -X POST http://localhost:8080/api/v1/organisations \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Tech Corp",
    "registrationNumber": "REG-001",
    "taxId": "TAX-12345",
    "industry": "Technology",
    "country": "USA",
    "contactEmail": "contact@techcorp.com",
    "status": "ACTIVE"
  }'
```

### Create Department
```bash
curl -X POST http://localhost:8080/api/v1/departments \
  -H "Content-Type: application/json" \
  -d '{
    "name": "IT Department",
    "departmentCode": "IT-001",
    "costCenterCode": "CC-001",
    "status": "ACTIVE",
    "organisationId": "your-org-uuid"
  }'
```

### Create Asset
```bash
curl -X POST http://localhost:8080/api/v1/assets \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Dell Laptop",
    "assetTag": "ASSET-001",
    "serialNumber": "SN-12345",
    "assetType": "HARDWARE",
    "manufacturer": "Dell",
    "model": "XPS 15",
    "purchaseDate": "2025-01-15",
    "purchaseCost": 1500.00,
    "usefulLifeMonths": 60,
    "status": "IN_USE",
    "condition": "GOOD",
    "departmentId": "your-dept-uuid",
    "organisationId": "your-org-uuid"
  }'
```

## Project Structure

```
src/
├── main/
│   ├── java/com/example/demo/
│   │   ├── aspect/                 # AOP aspects
│   │   ├── config/                 # Configurations
│   │   ├── controllers/
│   │   │   └── v1/                # REST controllers
│   │   ├── dto/                    # Data Transfer Objects
│   │   ├── enums/                  # Enumerations
│   │   ├── models/                 # Entity models
│   │   ├── repositories/           # Data access layer
│   │   ├── security/               # Security configs
│   │   └── services/
│   │       ├── interfaces/         # Service interfaces
│   │       └── impl/               # Service implementations
│   └── resources/
│       └── application.properties   # Configuration
└── test/                            # Test cases
```

## Troubleshooting

### Database Connection Issues
```bash
# Test PostgreSQL connection
psql -h localhost -U asset_user -d asset_management
```

### Maven Build Issues
```bash
# Clear Maven cache
mvn clean
rm -rf ~/.m2/repository

# Rebuild
mvn clean install -DskipTests
```

### Port Already in Use
```bash
# Change port in application.properties or via environment
SERVER_PORT=8081 mvn spring-boot:run
```

### DDL Issues
If tables don't auto-create:
1. Check `spring.jpa.hibernate.ddl-auto` is set to `update`
2. Verify database user has CREATE TABLE permissions
3. Check PostgreSQL logs: `tail -f /var/log/postgresql/postgresql.log`

## Monitoring

### Enable Actuator
Add to application.properties:
```properties
management.endpoints.web.exposure.include=health,info,metrics
```

Access:
- Health: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/metrics`

## Common Issues & Solutions

### Issue: Liquibase/Flyway migration conflicts
**Solution**: Remove automatic migrations and use update mode instead

### Issue: Lazy Loading Exception
**Solution**: Use `@Transactional` on service methods or implement DTO mapping

### Issue: JSON serialization errors
**Solution**: Check `@JsonIgnore` annotations on entity relationships

## Performance Tuning

### Database Connection Pooling
```properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
```

### Batch Operations
```properties
spring.jpa.properties.hibernate.jdbc.batch_size=20
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

### Query Optimization
```properties
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=true
```

## Security Hardening

1. Change default JWT secret
2. Enable HTTPS in production
3. Configure CORS properly
4. Implement rate limiting
5. Use strong database passwords
6. Enable database encryption
7. Configure firewall rules

## Next Steps

1. **Authentication**: Implement login endpoint using JWT
2. **Authorization**: Add `@PreAuthorize` annotations to endpoints
3. **Testing**: Write unit and integration tests
4. **Documentation**: Generate API docs with OpenAPI/Swagger
5. **Monitoring**: Set up application monitoring
6. **Logging**: Configure centralized logging (ELK)
7. **CI/CD**: Set up automated testing and deployment

## Support & Resources

- Spring Boot Docs: https://spring.io/projects/spring-boot
- Spring Data JPA: https://spring.io/projects/spring-data-jpa
- Hibernate Docs: https://hibernate.org/orm/documentation/
- PostgreSQL Docs: https://www.postgresql.org/docs/

---

**Last Updated**: February 2026
**Version**: 1.0.0

