# Ecommerce Microservices Platform

A robust, cloud-native microservices e-commerce platform built with Spring Boot, Spring Cloud, Kafka, PostgreSQL, and Keycloak for Identity and Access Management. 

## Project Phases & Progress

### Phase 0: Base Architecture & Scaffolding
- **Multi-Module Structure**: Initialized a complete Maven multi-module monorepo structure.
- **Microservices Stubs**: Generated boilerplate Spring Boot apps for core domains including Identity, Inventory, Order, Payment, Product Catalog, Search, Review, Cart, Notification, and Shipping.
- **Shared Libraries**: Built out a `common-exceptions` package for global error handling mapping to RFC-7807 problem details, and a `common-security` package containing the core `KeycloakJwtConverter` and shared `Roles` constants.
- **Docker Compose**: Defined the baseline backing infrastructure stack, including `PostgreSQL` (multi-DB initialization), `Redis`, `Zookeeper`, `Kafka`, `Kafka-UI`, and `Zipkin`.

### Phase 1: Identity Provider Configuration
- **Keycloak IAM**: Deployed an optimized Keycloak instance running in Docker, mapped to the dedicated `identity_db`.
- **Realm Configuration**: Bootstrapped an `ecommerce` realm with configured frontend/backend clients and base roles.
- **Gateway Foundation**: Initialized the Spring Cloud Gateway as the universal Edge entrypoint.

### Phase 2: Core Infrastructure & Identity Service Refactoring
- **Centralized Configuration**: Scaled the configuration layer by deploying the **Spring Cloud Config Server**. All service configurations are now securely provisioned from the externalized `config-repo`.
- **Service Discovery**: Implemented **Netflix Eureka** to dynamically manage service registry and scaling health checks.
- **Production-Grade Identity Service**:
  - Implemented secure API Registration and Login endpoints, fully orchestrating Keycloak via the Admin Client REST API.
  - Implemented programmatic and stateless authentication flows, automatically binding the `CUSTOMER` role inside Keycloak on successful registration.
  - Resolved dynamic database migrations with `Flyway` while sharing the database correctly with native Keycloak tables.
- **Admin APIs**: Developed secure APIs mapping to `@PreAuthorize("hasRole('ADMIN')")` allowing full lifecycle control (Soft-Deleting users natively across PostgreSQL and Keycloak, altering User Statuses, and manually assigning roles).
- **Event-Driven Architecture**: Implemented the Transactional Outbox Pattern to buffer Kafka domain events (e.g., `user.registered`, `user.deleted`) to broadcast state changes reliably across the rest of the ecosystem.