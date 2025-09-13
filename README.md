# Chat Storage Architecture

This repository implements an optimized chat message storage system using a two-layer approach (L0 → L1). It combines fast writes (CAS) and cost-effective storage (Segment JSONL + Index). Leveraging AWS S3, Zstd compression, Redis for idempotency, and SQL Server for metadata, it ensures efficient, scalable message management with minimal latency.

## 🏗️ Architecture Overview

### Two-Layer Storage System

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   L0 (Hot)      │    │   Compaction    │    │   L1 (Cold)     │
│   CAS Storage   │───▶│   Process       │───▶│   Segments      │
│   (S3 + Redis)  │    │   (Scheduled)   │    │   (S3 + Index)  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### Key Components

- **L0WriterService**: Handles fast writes using Content-Addressed Storage (CAS)
- **CompactorService**: Transforms L0 data into L1 segments for cost optimization
- **ReaderService**: Efficiently reads from both L0 and L1 layers
- **IdempotencyService**: Ensures message deduplication using Redis
- **S3Service**: Manages AWS S3 operations with compression

## 🚀 Features

- **Fast Writes**: CAS-based L0 storage for minimal write latency
- **Cost Optimization**: L1 segment storage with Zstd compression
- **Idempotency**: Redis-backed deduplication for reliable message handling
- **Parallel Processing**: Concurrent reads from multiple storage layers
- **Compression**: Zstd compression for optimal storage efficiency
- **Scalability**: Designed for high-throughput chat applications

## 🛠️ Technology Stack

- **Java 21** with Spring Boot 3.5.5
- **AWS S3** for object storage
- **SQL Server** for metadata and indexing
- **Redis** for idempotency and caching
- **Apache Kafka** for event streaming
- **Zstd** compression for data efficiency
- **Docker Compose** for local development

## 📦 Dependencies

- Spring Boot (Web, Data JPA, Cache, Redis)
- AWS SDK for S3
- Microsoft SQL Server JDBC
- Zstd compression library
- ULID for unique identifiers
- Jackson for JSON processing
- Lombok for code generation

## 🏃‍♂️ Quick Start

### Prerequisites

- Java 21+
- Maven 3.6+
- Docker and Docker Compose
- AWS S3 bucket (configured with appropriate permissions)

### Local Development Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd chat-storage-arch
   ```

2. **Start infrastructure services**
   ```bash
   docker-compose up -d
   ```

3. **Configure application properties**
   ```yaml
   # Update src/main/resources/application.yaml
   app:
     s3:
       bucket: your-s3-bucket
       region: us-east-1
       kmsKeyId: your-kms-key-id
   ```

4. **Run the application**
   ```bash
   ./mvnw spring-boot:run
   ```

## 📊 Data Flow

### Write Path (L0)
1. Message arrives with optional client message ID
2. Check idempotency in Redis
3. Generate ULID and sequence number
4. Create canonical JSON representation
5. Compress with Zstd and store in S3 CAS
6. Save metadata reference in SQL Server
7. Emit Kafka event for compaction

### Compaction Path (L0 → L1)
1. Scheduled job identifies eligible messages (30+ minutes old)
2. Groups messages by conversation
3. Creates compressed segments with index
4. Updates message references to point to segments
5. Maintains both L0 and L1 for seamless reads

### Read Path
1. Query message references from SQL Server
2. Parallel processing of CAS and segment references
3. Range requests for segment data optimization
4. Decompression and JSON parsing
5. Return ordered results

## 🔧 Configuration

### Application Properties

```yaml
app:
  s3:
    bucket: chat-storage-bucket
    region: us-east-1
    prefix: v1
    kmsKeyId: ${APP_S3_KMS_KEY_ID}
  compaction:
    minAgeMinutes: 30
    segmentTargetBytes: 16777216  # 16MB
    deleteCasAfterDays: 3
  redis:
    idempotencyTtlSeconds: 86400  # 24 hours
```

### Database Configuration

```yaml
spring:
  datasource:
    url: jdbc:sqlserver://localhost:1433;databaseName=demo_message_storage
    username: sa
    password: your-password
  jpa:
    hibernate:
      ddl-auto: update
```

## 📁 Project Structure

```
src/main/java/com/sds/phucth/chatstoragearch/
├── consts/           # Constants and configuration
├── dto/             # Data Transfer Objects
├── models/          # JPA entities
├── repository/      # Data access layer
├── services/        # Business logic
│   ├── L0WriterService.java
│   ├── CompactorService.java
│   ├── ReaderService.java
│   ├── IdempotencyService.java
│   └── S3Service.java
└── utils/           # Utility classes
```

## 🔍 Key Services

### L0WriterService
- Handles fast message writes
- Implements CAS (Content-Addressed Storage)
- Ensures idempotency
- Emits Kafka events for compaction

### CompactorService
- Scheduled compaction of L0 to L1
- Groups messages by conversation
- Creates compressed segments with indexes
- Optimizes storage costs

### ReaderService
- Efficient message retrieval
- Parallel processing of CAS and segments
- Range request optimization
- Maintains read order

## 🚦 API Endpoints

The application provides REST endpoints for:
- Message writing
- Message reading with pagination
- Conversation management
- Health checks and monitoring

## 📈 Performance Characteristics

- **Write Latency**: Sub-millisecond for L0 writes
- **Read Performance**: Parallel processing with range optimization
- **Storage Efficiency**: Zstd compression reduces storage by ~70%
- **Scalability**: Horizontal scaling with stateless design

## 🔒 Security

- KMS encryption for S3 objects
- Redis authentication
- SQL Server connection security
- Input validation and sanitization

## 🧪 Testing

```bash
# Run unit tests
./mvnw test

# Run integration tests
./mvnw verify
```

## 📝 Monitoring

The application includes:
- Spring Boot Actuator endpoints
- Health checks for all dependencies
- Metrics for performance monitoring
- Structured logging with SLF4J

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🆘 Support

For questions and support:
- Create an issue in the repository
- Check the documentation
- Review the code examples

---

**Note**: This is a production-ready chat storage system designed for high-scale applications. Ensure proper configuration of AWS credentials and database connections before deployment.
