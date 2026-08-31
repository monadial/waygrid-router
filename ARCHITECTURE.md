# Waygrid Router - Architecture Diagrams

## System Overview

```mermaid
graph TB
    subgraph "Event Sources (Origins)"
        HTTP[HTTP Origin<br/>REST API]
    end
    
    subgraph "Core System Services"
        TOPO[Topology Service<br/>Graph Management]
        WAYS[Waystation Service<br/>Routing Nodes] 
        HIST[History Service<br/>Event Tracking]
        SCHED[Scheduler Service<br/>Time-based Events]
    end
    
    subgraph "Event Processing" 
        KAFKA[Kafka Cluster<br/>3 Nodes]
        PROC[OpenAI Processor<br/>AI Transformation]
    end
    
    subgraph "Event Destinations"
        WEBHOOK[Webhook Destination<br/>HTTP POST]
        WS[WebSocket Destination<br/>Real-time Streams]
        BLACK[Blackhole Destination<br/>Event Discard]
    end
    
    subgraph "Infrastructure"
        DOCKER[Docker Containers]
        K8S[Kubernetes Operator]
        REDIS[RedPanda Console<br/>Kafka Management]
    end
    
    HTTP --> KAFKA
    KAFKA --> TOPO
    KAFKA --> WAYS  
    KAFKA --> HIST
    KAFKA --> SCHED
    KAFKA --> PROC
    PROC --> KAFKA
    KAFKA --> WEBHOOK
    KAFKA --> WS
    KAFKA --> BLACK
    
    DOCKER -.-> TOPO
    DOCKER -.-> WAYS
    DOCKER -.-> HIST
    DOCKER -.-> SCHED
    K8S -.-> DOCKER
    REDIS -.-> KAFKA
```

## Component Architecture

```mermaid
graph TB
    subgraph "Domain Layer"
        EVENT[Event Models]
        NODE[Node Models]
        TOPO_M[Topology Models]
        VALUE[Value Objects]
    end
    
    subgraph "Application Layer"
        ALG[Algebras<br/>Interfaces]
        INT[Interpreters<br/>Implementations]
        ACTOR[Actor System<br/>Supervision]
    end
    
    subgraph "Infrastructure Layer"
        HTTP4S[HTTP4s Server]
        KAFKA_I[Kafka Integration]
        DOCKER_I[Docker Packaging]
        LOG[Odin Logging]
    end
    
    ALG --> EVENT
    ALG --> NODE
    ALG --> TOPO_M
    INT --> ALG
    ACTOR --> INT
    HTTP4S --> ACTOR
    KAFKA_I --> ACTOR
    LOG --> ACTOR
    DOCKER_I --> HTTP4S
    DOCKER_I --> KAFKA_I
```

## Event Flow Architecture

```mermaid
sequenceDiagram
    participant Client
    participant HTTP_Origin
    participant Kafka
    participant Topology
    participant Processor
    participant Destination
    
    Client->>HTTP_Origin: HTTP Request
    HTTP_Origin->>Kafka: Publish Event
    Kafka->>Topology: Route Event
    Topology->>Kafka: Routing Decision
    Kafka->>Processor: Transform Event
    Processor->>Kafka: Processed Event
    Kafka->>Destination: Deliver Event
    Destination-->>Client: Response/Notification
```

## Technology Stack

```mermaid
graph TB
    subgraph "Programming Languages"
        SCALA[Scala 3.6.3]
    end
    
    subgraph "Functional Programming"
        CATS[Cats Effect 3.6.1<br/>Effect System]
        FS2[fs2 3.12.0<br/>Functional Streams]
        REFINED[Refined Types<br/>Compile-time Validation]
        MONOCLE[Monocle<br/>Functional Optics]
    end
    
    subgraph "HTTP & JSON"
        HTTP4S_STACK[HTTP4s 1.0.0-M44<br/>Type-safe HTTP]
        CIRCE[Circe 0.14.10<br/>JSON Processing]
    end
    
    subgraph "Concurrency & Messaging"
        ACTORS[cats-actors<br/>Actor Model]
        KAFKA_STACK[Kafka Integration<br/>Event Streaming]
    end
    
    subgraph "Infrastructure & Deployment"
        DOCKER_STACK[Docker<br/>Containerization]
        K8S_STACK[Kubernetes<br/>Orchestration]
        SBT[SBT<br/>Build Tool]
    end
    
    subgraph "Testing & Quality"
        WEAVER[Weaver<br/>Property Testing]
        SCALACHECK[ScalaCheck<br/>Generative Testing]
        DISCIPLINE[Discipline<br/>Law Testing]
    end
    
    SCALA --> CATS
    SCALA --> FS2
    SCALA --> HTTP4S_STACK
    SCALA --> CIRCE
    CATS --> ACTORS
    FS2 --> KAFKA_STACK
    DOCKER_STACK --> K8S_STACK
    SBT --> DOCKER_STACK
```

## Deployment Architecture

```mermaid
graph TB
    subgraph "Kubernetes Cluster"
        subgraph "Waygrid Namespace"
            PODS[Service Pods<br/>Topology, Waystation,<br/>History, Scheduler]
            CONFIG[ConfigMaps<br/>Application Config]
            SECRETS[Secrets<br/>API Keys, Certs]
        end
        
        subgraph "Kafka Namespace"
            KAFKA_PODS[Kafka Brokers<br/>3 Node Cluster]
            KAFKA_PVC[Persistent Volumes<br/>Event Storage]
        end
        
        subgraph "Monitoring Namespace"
            PROMETHEUS[Prometheus<br/>Metrics Collection]
            GRAFANA[Grafana<br/>Dashboards]
            REDPANDA[RedPanda Console<br/>Kafka Management]
        end
    end
    
    subgraph "External Services"
        OPENAI[OpenAI API<br/>AI Processing]
        WEBHOOKS[External Webhooks<br/>Event Delivery]
    end
    
    PODS --> KAFKA_PODS
    PODS --> CONFIG
    PODS --> SECRETS
    PODS --> OPENAI
    PODS --> WEBHOOKS
    PROMETHEUS --> PODS
    PROMETHEUS --> KAFKA_PODS
    GRAFANA --> PROMETHEUS
    REDPANDA --> KAFKA_PODS
```

## Data Flow Patterns

```mermaid
graph LR
    subgraph "Input Processing"
        IN[Input Event]
        VALIDATE[Validation]
        PARSE[Parsing]
    end
    
    subgraph "Routing Engine"
        TOPOLOGY[Topology Graph]
        DECISION[Routing Decision]
        FILTER[Event Filtering]
    end
    
    subgraph "Processing Pipeline"
        TRANSFORM[Event Transform]
        ENRICH[Data Enrichment]
        AI[AI Processing]
    end
    
    subgraph "Output Delivery"
        FORMAT[Output Formatting]
        DELIVERY[Delivery Method]
        CONFIRM[Confirmation]
    end
    
    IN --> VALIDATE
    VALIDATE --> PARSE
    PARSE --> TOPOLOGY
    TOPOLOGY --> DECISION
    DECISION --> FILTER
    FILTER --> TRANSFORM
    TRANSFORM --> ENRICH
    ENRICH --> AI
    AI --> FORMAT
    FORMAT --> DELIVERY
    DELIVERY --> CONFIRM
```

This architecture demonstrates a modern, scalable, and maintainable event-driven system built with functional programming principles and industry best practices.