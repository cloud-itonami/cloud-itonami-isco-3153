# Contributing

Contributions are welcome! This is a cloud-itonami blueprint project for flight operations support & dispatch (ISCO-08 3153).

## How to Contribute

1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/your-feature`).
3. Make your changes.
4. Write or update tests to ensure your changes work.
5. Run tests locally:
   ```bash
   clojure -M:test
   ```
6. Commit with clear, descriptive messages.
7. Push to your fork and open a pull request.

## Testing

All code changes must include tests. Run the full test suite before submitting:

```bash
clojure -M:test
```

## Code Style

- Follow Clojure conventions and idiomatic style.
- Use meaningful variable and function names.
- Add docstrings to public functions.
- Ensure `.cljc` files are portable across runtimes (no JVM-only constructs).

## Safety & Scope

**Remember**: This actor supports pre-flight / back-office / ground operations ONLY.
Any proposal or change that touches flight control, go/no-go decisions, airworthiness,
crew authority, or real-time in-flight operations is out of scope and will be rejected.

All hard safety invariants and scope exclusions are defined in `src/flight_operations/governor.cljc`.
Do not modify these without explicit consensus from the cloud-itonami maintainers.

## Questions?

Open an issue for questions, feature requests, or bug reports. Be as specific as possible.
