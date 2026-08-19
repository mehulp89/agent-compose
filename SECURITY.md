# Security policy

## Supported versions

Before `1.0.0`, only the latest released version receives security fixes.

## Report a vulnerability

Do not create a public issue for a suspected vulnerability. Contact the maintainer privately using
the contact method on <https://github.com/mehulp89> and include:

- affected version or commit;
- impact and realistic attack scenario;
- reproduction steps or a minimal project;
- any suggested mitigation;
- whether the report may be credited after a fix.

Please allow a reasonable response window before disclosing the issue publicly. The maintainer will
acknowledge the report, assess severity, prepare a fix, and coordinate disclosure when possible.

## Security boundaries

AgentCompose renders data and coordinates approval; it is not an authentication or authorization
system. Host applications remain responsible for:

- authenticating users and backend requests;
- validating model-generated tool names and arguments;
- requesting platform permissions;
- protecting provider credentials;
- preventing destructive or financially meaningful actions without suitable confirmation;
- defining retention and logging policies for prompts and attachments.
