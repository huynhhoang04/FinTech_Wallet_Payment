# Bruno Skills

A reusable agent-skill package for **API testing with [Bruno](https://usebruno.com)**. It is
distilled from a real project workspace (37 REST requests + 8 gRPC requests, HMAC-signed
gateway traffic, webhook signatures, tunnel-only internal services) so that the next project
starts with a working convention instead of a blank folder.

Drop it into any repository and an AI coding agent (or a human) can create and maintain a
Bruno workspace without re-deciding layout, naming, secret handling, or scripting patterns.

## What is inside

```
bruno-skills/
├─ skills/
│  └─ bruno-api-testing/
│     ├─ SKILL.md                       # the agent skill: conventions and rules
│     └─ references/
│        ├─ workspace-layout.md         # folder layout, file formats, environment schema
│        ├─ scripting-recipes.md        # copy-paste Bruno scripts (auth, HMAC, tests)
│        └─ safety-checklist.md         # side effects, secrets, runner, production rules
└─ templates/
   ├─ workspace.yml                     # Bruno workspace manifest (OpenCollection 1.0.0)
   ├─ README.md                         # starter README for a project's API workspace
   ├─ .gitignore                        # keeps secrets and local overrides out of git
   ├─ collections/
   │  ├─ 01-public-rest/                # OpenCollection YAML collection + 2 sample requests
   │  └─ 02-grpc/                       # native .bru gRPC collection + sample request
   └─ environments/
      └─ local.yml                      # environment schema with secret markers
```

## How to use it in a new project

1. **Copy the templates** to the place the project keeps its API workspace, e.g. `api-tests/`:

   ```bash
   cp -r bruno-skills/templates <project>/api-tests
   ```

2. **Install the skill** where your agent reads skills from. The convention used by the source
   project is `.agents/skills/<name>/SKILL.md` with symlinks for each tool:

   ```bash
   mkdir -p <project>/.agents/skills
   cp -r bruno-skills/skills/bruno-api-testing <project>/.agents/skills/
   ln -s ../.agents/skills <project>/.claude/skills     # if the tool reads .claude/skills
   ```

3. **Rename** the workspace in `workspace.yml`, the collection names, and the sample requests
   to match the project's API. Keep the numbering convention (`NN - Name`, zero-padded `seq`).

4. **Open the folder in Bruno** (`Open Workspace`), pick or create an environment, and fill
   variables. Anything marked `secret: true` goes into Bruno's **Secret Variables** tab and is
   stored encrypted locally - never into a committed file.

5. **Point the agent at the skill**: "add a request to the Bruno workspace" is enough - the
   skill covers naming, scripts, secrets, and safety from there.

## What the skill standardizes

- Workspace layout: `workspace.yml`, numbered collections, `environments/` at both workspace
  and collection level.
- Format choice: OpenCollection YAML for REST collections, native `.bru` for gRPC (never mixed
  in one collection).
- Naming and numbering so requests stay in call order (`01-auth---login.yml`, `seq: 1`).
- Scripting recipes: bearer token capture, HMAC request signing, webhook body signatures,
  test assertions - with no secret ever printed or committed.
- Side-effect discipline: every mutating request is labelled, idempotency keys are handled
  explicitly, and no one runs a collection runner blindly over a workspace with writes.

## Safety notes

- Secrets (HMAC keys, provider keys, tokens, passwords) never live in committed files. Use
  Bruno's encrypted secret variables or a local `*.local.yml` override (both are gitignored).
- Requests with side effects are meant to be sent one at a time from the Bruno app. The
  template sample requests document this in their `docs:` field.
- The templates ship with loopback/placeholder URLs on purpose. Real hostnames belong in
  environments, not in request files.

## Reusing across projects

This folder is self-contained: no build step, no dependencies. Keep it as an internal package
repository (copy out of it), or vendor it into each project. When a project improves a recipe,
copy the improvement back here so the next project inherits it.
