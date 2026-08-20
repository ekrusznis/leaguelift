# Local QA credentials

This directory is gitignored.

Create `<suite>.username` and `<suite>.password` files for the suite you run.

Examples:

- `coach.username`
- `coach.password`
- `owner.username`
- `owner.password`
- `authority_owner-viewer.username`
- `authority_owner-viewer.password`
- `subscription_plan.username`
- `subscription_plan.password`

`run-suite.sh` converts slashes in a suite name to underscores when choosing credential
file names.
