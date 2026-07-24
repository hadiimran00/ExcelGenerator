# Selenium Data-Driven Automation Framework

A Java-based Selenium automation framework for executing configurable, data-driven UI test scenarios.

The framework reads test definitions from Excel configuration files, executes browser automation, performs configurable validations, generates execution reports, and supports running multiple test users from a single execution.

## Features

- Data-driven test execution
- Selenium WebDriver automation
- Page Object Model (POM)
- Configurable execution through Excel
- Multi-user execution
- Automatic file upload and download support
- Excel file generation and modification
- Configurable validation framework
- Screenshot capture on failures
- HTML and text execution reports
- Log4j2 logging
- Headless browser support

## Technology Stack

- Java
- Selenium WebDriver
- Maven
- Apache POI
- Jackson
- Log4j2
- WebDriverManager

## Project Structure

```
src
├── pages/
├── utilities/
├── Main.java
├── resources/
│   ├── application.properties
│   └── log4j2.xml
├── users.json
└── README.md
```

## Getting Started

### Prerequisites

- Java 17 or later
- Maven
- Google Chrome

### Clone the Repository

```bash
git clone https://github.com/<your-username>/<repository>.git
```

### Install Dependencies

```bash
mvn clean install
```

### Configure Test Users

Create or update the `users.json` file with the required test credentials and execution settings.

### Configure Framework Settings

Modify `application.properties` to configure options such as:

- Headless execution
- Download directory
- Browser zoom level

### Run the Framework

Using Maven:

```bash
mvn exec:java
```

Or run `Main.java` directly from your IDE.

## Configuration

The framework is driven by configuration files rather than hardcoded test logic.

Configuration supports:

- Test execution control
- Screen definitions
- Test parameters
- Validation rules
- Scenario-specific data

## Framework Workflow

```
Initialize
    ↓
Load Configuration
    ↓
Start Browser
    ↓
Authenticate
    ↓
Execute Configured Tests
    ↓
Run Validations
    ↓
Generate Reports
    ↓
Close Browser
```

## Reports

The framework generates execution artifacts including:

- HTML summary report
- Text summary
- Log files
- Screenshots for failed tests
- Downloaded test files
- Generated test files

## Design Principles

- Modular architecture
- Reusable utilities
- Separation of concerns
- Data-driven execution
- Configurable validations
- Easy extensibility

## Future Enhancements

- Parallel execution
- Additional reporting integrations
- Retry support
- CI/CD integration
- Cross-browser execution
- Containerized execution

## Contributing

Contributions are welcome through pull requests. Please ensure new functionality includes appropriate tests and follows the project's coding conventions.

## License

Add the appropriate license before publishing the repository.
