# Study Buddy (Java, CLI)

A minimal, **original** spaced-repetition flashcard app for the terminal. Stores your decks and review state in a JSON file; uses a simplified SM-2 scheduler.

## Features
- Create decks and cards (front/back)
- Daily review session with spaced repetition (simplified SM-2 algorithm)
- JSON persistence (`~/.studybuddy/decks.json` by default)
- JUnit 5 tests
- Maven build

## Quickstart
```bash
# build & run
mvn -q -DskipTests package
java -jar target/study-buddy-1.0.0.jar --help
```

Example:
```bash
java -jar target/study-buddy-1.0.0.jar add-deck "Discrete Math"
java -jar target/study-buddy-1.0.0.jar add-card "Discrete Math" "What is a bijection?" "A one-to-one and onto function."
java -jar target/study-buddy-1.0.0.jar review
```

## Tests
```bash
mvn -q test
```

## License
MIT
