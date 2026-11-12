<!--
SYNC IMPACT REPORT
==================
Version change: [CONSTITUTION_VERSION] → 1.0.0 (initial ratification)
Modified principles:
  - [PRINCIPLE_1_NAME] → I. Code Quality
  - [PRINCIPLE_2_NAME] → II. Testing Standards
  - [PRINCIPLE_3_NAME] → III. User Experience Consistency
  - [PRINCIPLE_4_NAME] → IV. Performance Requirements
  - [PRINCIPLE_5_NAME] → (removed — user specified 4 principles)
Added sections:
  - Quality Gates (replaces [SECTION_2_NAME])
  - Development Workflow (replaces [SECTION_3_NAME])
Removed sections: None (all template placeholders filled)
Templates requiring updates:
  ✅ .specify/templates/plan-template.md — Constitution Check section aligns with all four principles
  ✅ .specify/templates/spec-template.md — Success Criteria aligns with UX consistency and performance principles
  ✅ .specify/templates/tasks-template.md — Task phases reflect testing and quality gate principles
Deferred TODOs: None
-->

# SampleProject Constitution

## Core Principles

### I. Code Quality

Every unit of code introduced into this project MUST be readable, maintainable, and purposefully
scoped. Complexity MUST be justified — if a simpler approach achieves the same goal, the simpler
approach wins. Abstractions MUST serve at least two concrete use cases before being introduced;
single-use helpers are prohibited. Dead code, unused variables, and vestigial comments MUST be
removed rather than commented out or retained for hypothetical future use.

- Code MUST follow the established style guide and linting rules without exception.
- Functions and methods MUST have a single, clearly defined responsibility.
- No magic numbers or unexplained constants — all non-obvious values MUST be named and documented
  at the point of declaration.
- Backward-compatibility shims, feature flags for unreleased work, and TODO comments older than
  one release cycle MUST NOT accumulate in the codebase.

**Rationale**: Readable code reduces onboarding time, lowers defect rates, and makes future
changes safer. Complexity left unjustified becomes technical debt that compounds.

### II. Testing Standards

Tests are first-class artifacts, not afterthoughts. For any feature work, tests MUST be written
before implementation begins (TDD: Red → Green → Refactor). A feature is not complete until all
acceptance criteria are covered by automated tests that were verified to fail before the
implementation was added.

- Unit tests MUST cover all non-trivial logic branches with explicit assertions.
- Integration tests MUST be written for every inter-component contract (API boundaries, data
  access layers, shared schemas) and MUST run against real dependencies, not mocks, unless the
  dependency is external and uncontrollable.
- Test coverage MUST NOT regress below the project baseline on any merged change.
- Flaky tests MUST be fixed or removed immediately — a flaky test provides no safety guarantee
  and MUST be treated as a P1 defect.
- Tests MUST be independently runnable and MUST NOT depend on global state, execution order,
  or environment-specific configuration beyond documented setup steps.

**Rationale**: Tests written after the fact tend to confirm existing behavior rather than
validate requirements. Mandatory pre-implementation tests ensure the team agrees on correctness
criteria before code is written.

### III. User Experience Consistency

Every user-facing interaction — screens, dialogs, error messages, loading states, and navigation
flows — MUST conform to the project's established UX patterns and design system. Inconsistency
in behavior or visual presentation is a defect, not a style preference.

- UI components MUST reuse the shared design system tokens (colors, typography, spacing, icons).
  Custom styling that duplicates or overrides design system values MUST NOT be introduced without
  explicit design review and approval.
- Error states MUST be informative, actionable, and phrased in plain language. Generic error
  messages ("Something went wrong") MUST NOT be the only user-visible feedback.
- Navigation and interaction patterns MUST be consistent within the same platform and feature
  area. A user who learns one flow MUST be able to apply that mental model to adjacent flows.
- Accessibility (a11y) MUST be considered at design time, not post-implementation. All
  interactive elements MUST have accessible labels and MUST meet WCAG 2.1 AA contrast ratios.

**Rationale**: Users build mental models from repeated patterns. Inconsistency forces cognitive
re-learning, erodes trust, and increases support burden. A coherent UX is a product requirement,
not a polish task.

### IV. Performance Requirements

Performance targets are requirements, not aspirations. Every feature MUST define measurable
performance criteria in its specification before implementation begins. Implementation that
does not meet its declared targets MUST NOT be merged.

- UI interactions (taps, transitions, list scrolling) MUST maintain 60 fps on the minimum
  supported device tier. Frame drops below 50 fps are considered a defect.
- Network-dependent screens MUST implement and display loading states. Cold-start data fetches
  MUST complete or surface a meaningful partial state within 3 seconds on a 4G connection.
- Memory growth MUST be bounded — features MUST NOT introduce memory leaks detectable via
  profiling tools (e.g., LeakCanary, Android Profiler).
- Database queries and local I/O MUST be executed off the main thread. Blocking the main thread
  for more than 16ms is a P1 defect.
- Performance regressions introduced by a change MUST be identified and resolved before merge,
  not deferred as follow-up work.

**Rationale**: Performance perceived by users directly affects retention and ratings. Treating
performance as a first-class requirement prevents the accumulation of regressions that become
prohibitively expensive to unwind.

## Quality Gates

All changes MUST pass the following gates before merge:

1. **Linting & Static Analysis**: Zero new warnings or errors introduced by the change.
2. **Test Suite**: Full test suite passes; no tests marked skip without documented justification.
3. **Coverage**: Test coverage at or above the project baseline for changed files.
4. **Performance Benchmark**: Automated performance checks pass; no measurable regression on
   defined benchmarks.
5. **UX Review**: Any change to user-facing surfaces has been reviewed against the design system
   by at least one designated UX reviewer.
6. **Constitution Check**: The implementing engineer MUST explicitly confirm in the PR description
   that each applicable principle was considered and satisfied.

No gate may be bypassed without explicit written approval from two maintainers and a documented
justification recorded in the PR.

## Development Workflow

- **Specification First**: A feature MUST have a written spec with acceptance criteria and
  performance targets before any implementation task is started.
- **Test Before Code**: Tests MUST be written and confirmed to fail before the corresponding
  implementation is authored.
- **Incremental Delivery**: Features MUST be broken into independently testable and deployable
  user story slices. A working increment MUST be demonstrable after each slice.
- **Review Requirements**: Every PR requires at least one peer code review and one passing CI
  run. UI changes require UX review. Architecture changes require a senior maintainer review.
- **Commit Discipline**: Commits MUST be atomic and descriptive. A commit message MUST explain
  *why* a change was made, not just what changed.

## Governance

This constitution supersedes all other documented practices, style guides, and informal
conventions. In the event of a conflict, the constitution takes precedence.

**Amendment Procedure**:
1. Propose an amendment via a dedicated PR that modifies this file.
2. Include in the PR: the motivation for the change, the principle being modified, and a
   migration plan for any existing code that conflicts with the updated principle.
3. Amendment requires approval from at least two maintainers.
4. Upon merge, `LAST_AMENDED_DATE` and `CONSTITUTION_VERSION` MUST be updated in the same
   commit.

**Versioning Policy**:
- MAJOR: Removal or fundamental redefinition of a principle.
- MINOR: Addition of a new principle or section, or material expansion of existing guidance.
- PATCH: Clarifications, wording improvements, or non-semantic refinements.

**Compliance Review**: Adherence to this constitution MUST be verified at every PR review.
Violations are defects and MUST be resolved, not documented and deferred.

**Version**: 1.0.0 | **Ratified**: 2026-03-19 | **Last Amended**: 2026-03-19
