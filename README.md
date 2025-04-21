# TaskMaster AI

## Project Overview

TaskMaster AI is an intelligent task management system that leverages artificial intelligence to analyze, prioritize, schedule, and execute tasks efficiently. The system uses natural language processing to understand task requirements and applies optimization algorithms to create optimal execution schedules.

## Architecture

The project is built on Spring Boot with Spring AI integration and consists of the following components:

### Core Domain Models

- **Task Entity**: Complete representation of a task with fields including:
  - Unique identifier
  - Description
  - Creation timestamp
  - Due date
  - Priority level
  - Current status
  - Estimated completion time
  - Dependencies
  - User assignments

- **TaskPriority Enum**: Classification levels for task urgency (LOW, MEDIUM, HIGH, URGENT)

- **TaskStatus Enum**: Task lifecycle states (CREATED, ANALYZED, SCHEDULED, IN_PROGRESS, COMPLETED, FAILED)

- **TaskExecutionResult**: Captures execution outcomes, including:
  - Execution timestamp
  - Completion status
  - Performance metrics
  - Generated outputs

### Data Layer

- **MongoDB Repository**: Persistent storage for all task-related data
  - **TaskRepository**: Interface with standard CRUD operations and custom query methods for:
    - Finding tasks by status
    - Retrieving tasks due within a timeframe
    - Filtering tasks by priority level
    - Locating dependent tasks

### AI Services

- **OllamaConfig**: Configuration for the AI model settings
  - Model selection (gemma3:4b or deepseek-r1:7b)
  - Temperature and token settings
  - Context management

- **StopModel**: Service to properly release AI resources when not in use

- **AIService**: Core intelligence providing:
  - Task analysis and priority determination
  - Generation of execution strategies
  - Response parsing and structured data extraction

### Business Logic Services

- **TaskMasterService**: Central service for task management operations
  - Task creation and validation
  - Status updates and lifecycle management
  - Result processing and feedback integration

- **TaskExecutionAgent**: Handles the execution logic for tasks
  - Strategy implementation
  - Progress monitoring
  - Error handling and recovery mechanisms

- **TaskSchedulingAgent**: Responsible for optimal task scheduling
  - Priority-based sorting
  - Dependency resolution
  - Resource allocation optimization

- **TaskMasterOrchestrator**: Coordinates overall system workflow
  - Triggers scheduled optimizations
  - Manages execution queues
  - Synchronizes system components

### REST API Controllers

- **ChatController**: Endpoint for direct interaction with the AI system
  - Natural language task submission
  - Query processing
  - Conversational assistance

- **TaskController**: Management endpoints for task operations
  - Task creation, retrieval, and updates
  - Schedule viewing and adjustments
  - Result reporting

### Configuration

- **Application Class**: Main Spring Boot application with scheduling enabled
- **Application Properties**: Environment-specific configuration settings

## System Workflow

### 1. Task Creation
When a user submits a task through the REST API, the system:
- Accepts the task description and metadata
- Passes the information to the AI service for analysis
- Determines appropriate priority levels based on content and context
- Calculates estimated completion time
- Identifies potential subtasks or dependencies
- Saves the analyzed task to the database

### 2. Schedule Optimization
The system regularly optimizes the execution schedule by:
- Evaluating all pending tasks in the queue
- Considering task priorities, deadlines, and dependencies
- Applying scheduling algorithms to create an efficient execution order
- Adjusting resource allocations based on current system load
- Updating task statuses and scheduled execution times

### 3. Task Execution
When a task is due for execution:
- The TaskExecutionAgent retrieves the task details
- The AI service generates a tailored execution strategy
- The system manages the execution process according to the strategy
- Progress is monitored and status updates are recorded
- Results are captured in the TaskExecutionResult object

### 4. Feedback Loop
After task completion:
- Execution results are analyzed for performance insights
- Successful strategies are recorded for future reference
- Task metadata (actual completion time, resource usage) is updated
- This information feeds back into the scheduling algorithm to improve future predictions

## Setup and Running Instructions

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- MongoDB 5.0+ running on port 27017
- Ollama installed and running on port 11434

### Environment Setup

1. **Install Ollama** (if not already installed):
   ```
   curl -fsSL https://ollama.com/install.sh | sh
   ```

2. **Pull the required AI model**:
   ```
   ollama pull gemma3:4b
   # OR
   ollama pull deepseek-r1:7b
   ```

3. **Start MongoDB**:
   ```
   mongod --dbpath=/path/to/data/directory
   ```

### Building and Running

1. **Clone the repository**:
   ```
   git clone https://github.com/prakashvk2003/TaskMaster-AI.git
   cd taskmaster-ai
   ```

2. **Build the project**:
   ```
   mvn clean package
   ```

3. **Run the application**:
   ```
   mvn spring-boot:run
   ```

4. The API will be available at `http://localhost:8080`

### API Endpoints

#### Task Management
- `POST /api/tasks` - Create a new task
- `GET /api/tasks` - List all tasks
- `GET /api/tasks/{id}` - Get a specific task
- `PUT /api/tasks/{id}` - Update a task
- `DELETE /api/tasks/{id}` - Delete a task
- `GET /api/tasks/status/{status}` - List tasks by status

#### AI Chat Interface
- `POST /api/chat` - Send a message to the AI assistant
- `POST /api/chat/analyze-task` - Get AI analysis of a potential task

## Configuration Options

The application can be configured through `application.properties`:

```properties
# Server configuration
server.port=8080

# MongoDB configuration
spring.data.mongodb.host=localhost
spring.data.mongodb.port=27017
spring.data.mongodb.database=taskmaster

# Ollama AI configuration
ollama.endpoint=http://localhost:11434/api/generate
ollama.model=gemma3:4b
ollama.temperature=0.7
ollama.max-tokens=2048

# Task scheduling configuration
taskmaster.scheduling.interval=300000
```

## Future Enhancements

- User authentication and multi-user support
- Task templates and recurring tasks
- Advanced analytics dashboard
- Mobile application interface
- Integration with external tools and services
