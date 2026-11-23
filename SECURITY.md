# Security policy

## Reporting a vulnerability

**Please do not report security problems in a public issue, discussion or pull request.**

Report them privately through GitHub:
[**Report a vulnerability**](https://github.com/zelytra/Librarius/security/advisories/new)
(the *Security* tab of the repository, then *Report a vulnerability*). Only the maintainer
can read the report.

Please include:

- what is affected (the web app, the API, the mobile app, the deployment) and the version or
  commit;
- the steps to reproduce it, or a proof of concept;
- what an attacker could do with it.

## What happens next

- An acknowledgement within **7 days**.
- An assessment, and a fix plan if the report is confirmed, within **30 days**.
- Once the fix is out, a public advisory credits you, unless you would rather stay
  anonymous.

## Scope

| In scope | Out of scope |
|---|---|
| The code in this repository: web app, API, mobile shell, Helm chart, CI workflows | Denial of service, load or volume testing |
| The staging deployment at `librarius.zelytra.fr`, **non-destructive checks only** | Social engineering, or physical attacks |
| | Anything that reads or changes another user's data: stop and report what you found |

Use your own test account only. Staging is a test environment and its data may be reset at
any time.

## Supported versions

Librarius has not shipped a stable release yet. Only the latest `main` gets security fixes.
