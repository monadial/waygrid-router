# Waygrid Router 🚀

> A modern, scalable event-driven routing and graph processing system built with Scala 3 and functional programming principles.

[![Scala Version](https://img.shields.io/badge/scala-3.6.3-blue.svg)](https://scala-lang.org/)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Docker](https://img.shields.io/badge/docker-ready-blue.svg)](https://www.docker.com/)

## 🌟 Overview

Waygrid is a sophisticated event-driven routing system that processes events through a configurable graph topology. Built with modern functional programming principles, it provides high-throughput, fault-tolerant event processing with intelligent routing capabilities.

### Key Features

- 🎯 **Event-Driven Architecture**: Pure functional event processing with Kafka backbone
- 🔄 **Graph Routing**: Intelligent topology-based event routing
- 🚀 **High Performance**: Built on cats-effect and fs2 for maximum throughput  
- 🛡️ **Fault Tolerant**: Actor-based supervision with graceful error recovery
- 📦 **Cloud Native**: Docker containers with Kubernetes operator
- 🤖 **AI Integration**: OpenAI processor for intelligent event transformation
- 🔧 **Extensible**: Plugin architecture for custom processors and destinations

## 🏗️ Architecture

```
Event Sources → Processing Pipeline → Destinations
     ↓              ↓                    ↓
   HTTP API    Topology/Routing     Webhooks
   (Origins)   Actor Supervision    WebSockets
               OpenAI Processing    Blackhole
```

### Core Components

- **System Services**: Topology, Waystation, History, Scheduler
- **Origins**: HTTP endpoints for event ingestion
- **Processors**: OpenAI integration and custom transformations  
- **Destinations**: Webhooks, WebSockets, and testing endpoints
- **Infrastructure**: Kafka clustering, Docker deployment, K8s operator

## 🚀 Quick Start

### Prerequisites

- Java 21+
- SBT 1.9+
- Docker & Docker Compose

### Local Development

1. **Clone the repository**
   ```bash
   git clone https://github.com/monadial/waygrid-router.git
   cd waygrid-router
   ```

2. **Start infrastructure services**
   ```bash
   docker-compose up -d
   ```

3. **Compile and run**
   ```bash
   sbt compile
   sbt "project system-topology" run
   ```

4. **Access services**
   - Kafka Console: http://localhost:1336
   - Application logs: Check console output

### Docker Deployment

Build all services:
```bash
sbt docker:publishLocal
```

Deploy with Docker Compose:
```bash
docker-compose -f docker-compose.yaml -f docker-compose.services.yaml up
```

## 📚 Documentation

- [📋 **Executive Summary**](EXECUTIVE_SUMMARY.md) - Quick overview and assessment
- [🏛️ **Architecture Guide**](ARCHITECTURE.md) - Detailed system architecture
- [📊 **Technical Assessment**](ASSESSMENT.md) - Comprehensive technical analysis
- [🐳 **Deployment Guide**](docs/deployment.md) - Production deployment instructions
- [🔧 **API Documentation**](docs/api.md) - REST API reference

## 🛠️ Technology Stack

### Core Technologies
- **Scala 3.6.3** - Modern functional programming language
- **Cats Effect 3.6.1** - Pure functional effects and concurrency
- **fs2 3.12.0** - Functional streaming for high-throughput processing
- **HTTP4s 1.0.0** - Type-safe HTTP server and client
- **Circe 0.14.10** - JSON processing with automatic derivation

### Infrastructure
- **Apache Kafka** - Event streaming backbone with 3-node cluster
- **Docker** - Containerization for all services
- **Kubernetes** - Container orchestration with custom operator
- **RedPanda Console** - Kafka management and monitoring

### Quality Assurance  
- **Refined Types** - Compile-time validation and type safety
- **Weaver** - Property-based testing framework
- **ScalaCheck** - Generative testing for robust validation

## 🏃‍♂️ Usage Examples

### Sending Events via HTTP

```bash
# Send a simple event
curl -X POST http://localhost:1337/events \
  -H "Content-Type: application/json" \
  -d '{
    "id": "evt-001",
    "type": "user.created", 
    "data": {"userId": "12345", "email": "user@example.com"}
  }'
```

### Configuring Routing Topology

```scala
// Define routing rules in application.conf
waygrid {
  topology {
    routes = [
      {
        from = "user.created"
        to = ["webhook.notification", "history.store"]
        conditions = ["data.email != null"]
      }
    ]
  }
}
```

## 🔧 Configuration

Key configuration files:
- `application.conf` - Main application settings
- `docker-compose.yaml` - Local development environment
- `build.sbt` - Build configuration and dependencies

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `WAYGRID_CLUSTER_ID` | Cluster identifier | `cluster-1` |
| `KAFKA_BROKERS` | Kafka bootstrap servers | `localhost:29092` |
| `LOG_LEVEL` | Logging level | `INFO` |

## 🧪 Testing

Run the test suite:
```bash
# Run all tests
sbt test

# Run specific module tests  
sbt "project common-domain" test

# Run property-based tests
sbt "testOnly *PropertySpec"
```

## 📈 Monitoring

### Available Metrics
- Event throughput and latency
- Processing pipeline performance  
- Error rates and recovery statistics
- Resource utilization metrics

### Dashboards
- RedPanda Console: http://localhost:1336 (Kafka monitoring)
- Application metrics: Integration with Prometheus/Grafana available

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

### Development Setup
1. Fork the repository
2. Create a feature branch
3. Make your changes with tests
4. Submit a pull request

### Code Standards
- Follow functional programming principles
- Use refined types for domain validation
- Write property-based tests for business logic
- Maintain immutability and purity

## 📄 License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

## 🏢 About Monadial

Waygrid is developed by [Monadial](https://monadial.com), specializing in functional programming and distributed systems.

**Contact**: [tomas@monadial.com](mailto:tomas@monadial.com)

---

⭐ **Star this repository if you find it useful!**

Infrastructure-as-code has been extracted to a sibling repository at `../waygrid-router-infra` (git already initialized there).
