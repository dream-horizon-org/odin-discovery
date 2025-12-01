# Odin Discovery Service

[![License: LGPL v3](https://img.shields.io/badge/License-LGPL_v3-blue.svg)](https://www.gnu.org/licenses/lgpl-3.0)

A high-performance, reactive service discovery management service built with Vert.x that provides a unified interface for managing DNS records and service discovery across multiple providers including AWS Route53 and Consul.

## Features

- **Multi-Provider Support**: Seamlessly manage service discovery across different providers:
  - AWS Route53
  - Consul
- **Batch Operations**: Efficiently process multiple DNS record operations in a single request
- **Reactive Architecture**: Built on Vert.x for high throughput and low latency
- **RESTful API**: Clean REST API with comprehensive Swagger documentation
- **Database Migrations**: Automated schema management using Liquibase
- **Health Checks**: Built-in health check endpoint for monitoring
- **Provider Caching**: Intelligent caching of discovery providers for improved performance

## Technology Stack

- **Java 17**: Modern Java with latest language features
- **Vert.x 4.2.7**: Reactive, non-blocking application framework
- **MySQL**: Relational database for persistence
- **gRPC**: High-performance RPC communication
- **Liquibase**: Database migration and versioning
- **Swagger/OpenAPI**: API documentation and exploration
- **Maven**: Build and dependency management

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- MySQL 8.0+
- Docker (optional, for containerized deployment)

## Getting Started

### Local Development Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/dream-horizon-org/odin-discovery.git
   cd odin-discovery
   ```

2. **Configure the database**

   Create a MySQL database:
   ```sql
   CREATE DATABASE odin_discovery_service;
   ```

3. **Configure application settings**

   Set environment variables or modify `src/main/resources/application.conf`:
   ```bash
   export ODIN_MYSQL_MASTER_HOST=localhost
   export ODIN_MYSQL_SLAVE_HOST=localhost
   export ODIN_MYSQL_USERNAME=root
   export ODIN_MYSQL_PASSWORD=your_password
   export ODIN_ACCOUNT_MANAGER_HOST=127.0.0.1
   export ODIN_ACCOUNT_MANAGER_PORT=8081
   ```

4. **Build the project**
   ```bash
   mvn clean package
   ```

5. **Run the service**
   ```bash
   java -jar target/odin-discovery-service/odin-discovery-service-fat.jar
   ```

   Or use Maven:
   ```bash
   mvn exec:java -Dexec.mainClass="com.dream11.odin.MainLauncher"
   ```

6. **Verify the service is running**

   Access the health check endpoint:
   ```bash
   curl http://localhost:8080/healthcheck
   ```

### Docker Deployment

The pre-built Docker image is available on Docker Hub at [https://hub.docker.com/r/odinhq/discovery](https://hub.docker.com/r/odinhq/discovery).

1. **Pull and run the pre-built image**
   ```bash
   docker pull odinhq/discovery:latest
   docker run -p 8080:8080 \
     -e ODIN_MYSQL_MASTER_HOST=your_db_host \
     -e ODIN_MYSQL_SLAVE_HOST=your_db_host \
     -e ODIN_MYSQL_USERNAME=your_username \
     -e ODIN_MYSQL_PASSWORD=your_password \
     -e ODIN_ACCOUNT_MANAGER_HOST=your_oam_host \
     -e ODIN_ACCOUNT_MANAGER_PORT=8081 \
     odinhq/discovery:latest
   ```

2. **Or build the Docker image locally**
   ```bash
   docker build -t odin-discovery .
   docker run -p 8080:8080 \
     -e ODIN_MYSQL_MASTER_HOST=your_db_host \
     -e ODIN_MYSQL_SLAVE_HOST=your_db_host \
     -e ODIN_MYSQL_USERNAME=your_username \
     -e ODIN_MYSQL_PASSWORD=your_password \
     -e ODIN_ODIN_ACCOUNT_MANAGER_HOST=your_oam_host \
     -e ODIN_ODIN_ACCOUNT_MANAGER_PORT=8081 \
     odin-discovery
   ```

## API Documentation

Once the service is running, you can access the interactive Swagger UI:

- **Swagger UI**: http://localhost:8080/swagger/
- **Swagger JSON**: http://localhost:8080/swagger/swagger.json
- **Swagger YAML**: http://localhost:8080/swagger/swagger.yaml

### API Endpoints

#### Health Check
```
GET /healthcheck
```
Returns the health status of the service.

#### Batch Record Operations
```
PUT /v1/record
Content-Type: application/json
Header: orgId: <organization-id>
{
  "records": [
    {
      "id": "record-1",
      "action": "UPSERT",
      "name": "example.com",
      "type": "A",
      "value": "192.168.1.1",
      "ttl": 300
    }
  ]
}
```

Supported actions:
- `UPSERT`: Create or update a DNS record
- `DELETE`: Delete a DNS record

## Configuration

The service can be configured via environment variables or configuration files. Key configuration options:

| Environment Variable | Description | Default |
|---------------------|-------------|---------|
| `ODIN_MYSQL_MASTER_HOST` | MySQL master host | localhost |
| `ODIN_MYSQL_SLAVE_HOST` | MySQL slave host | localhost |
| `ODIN_MYSQL_USERNAME` | Database username | root |
| `ODIN_MYSQL_PASSWORD` | Database password | (empty) |
| `ODIN_ACCOUNT_MANAGER_HOST` | Odin Account Manager host | 127.0.0.1 |
| `ODIN_ACCOUNT_MANAGER_PORT` | Odin Account Manager port | 8081 |

## Architecture

The service follows a reactive, event-driven architecture:

- **Verticals**: Modular units of deployment handling different concerns
- **Provider Pattern**: Pluggable discovery provider implementations
- **Dependency Injection**: Google Guice for dependency management
- **Reactive Streams**: RxJava3 for reactive programming

### Key Components

- **RecordService**: Core business logic for record management
- **DiscoveryProvider**: Interface for discovery provider implementations
- **DiscoveryProviderFactory**: Factory for creating and caching providers
- **RestVerticle**: HTTP server and REST API handling

## Development

### Running Tests

```bash
# Run unit tests
mvn test

# Run integration tests
mvn verify

# Run with coverage
mvn jacoco:report
```

### Code Formatting

The project uses the Google Java Format plugin:

```bash
mvn fmt:format
```

### Building

```bash
# Clean build
mvn clean package

# Skip tests
mvn clean package -DskipTests
```

## Database Migrations

Database schema changes are managed through Liquibase migrations located in `src/main/resources/db/mysql/`. Migrations are automatically applied on service startup.

## Contributing

We welcome contributions! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Guidelines

- Follow Java coding standards and best practices
- Write unit tests for new features
- Update documentation as needed
- Ensure all tests pass before submitting PRs

## License

This project is licensed under the GNU Lesser General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

## Support

For issues, questions, or contributions, please open an issue on the GitHub repository.
