# Contributing to Atom Guard

Thank you for your interest in contributing to Atom Guard! We welcome contributions from the community.

## Development Setup

1.  **Java 21**: Ensure you have JDK 21 installed.
2.  **Maven**: Ensure you have Maven 3.8+ installed.
3.  **IDE**: IntelliJ IDEA is recommended (Community or Ultimate).
4.  **Paper Server**: Set up a local Paper 1.21.4 server for testing.

## Building the Project

```bash
git clone https://github.com/ATOMGAMERAGA/AtomGuard.git
cd AtomGuard
mvn clean package
```

The compiled JAR will be in `core/target/AtomGuard-1.0.0.jar`.

## Code Standards

- **Language**: English for code (classes, variables, comments). Turkish/English for user-facing messages.
- **Style**: Google Java Style Guide.
- **Annotations**: Use `@NotNull` and `@Nullable` everywhere.
- **Logging**: Use `plugin.getLogger()` or the provided `LogManager`.

## Creating a Module

To create a new protection module:
1.  Extend `AbstractModule`.
2.  Register in `AtomGuard.java`.
3.  Add configuration in `config.yml`.

```java
public class MyNewModule extends AbstractModule {
    public MyNewModule(AtomGuard plugin) {
        super(plugin, "my-new-module");
    }
    // Implement logic
}
```

## Pull Request Process

1.  Fork the repository.
2.  Create a feature branch (`feat/new-module`).
3.  Commit your changes using [Conventional Commits](https://www.conventionalcommits.org/).
4.  Push to your fork and submit a Pull Request.
5.  Ensure all checks pass.

## License

By contributing, you agree that your contributions will be licensed under the project's BSD 3-Clause License.
