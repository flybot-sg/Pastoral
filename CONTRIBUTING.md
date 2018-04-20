# How to Contribute

This project is [Eclipse Public Licensed](LICENSE.txt) and accepts contributions via
GitHub pull requests.  This document outlines some of the conventions on
development workflow, commit message formatting, contact points and other
resources to make it easier to get your contribution accepted.

# Certificate of Origin

By contributing to this project you agree to the Developer Certificate of
Origin (DCO). This document was created by the Linux Kernel community and is a
simple statement that you, as a contributor, have the legal right to make the
contribution. See the [DCO](DCO) file for details.

# Contact

Use [GitHub issues](https://github.com/flybot-sg/pastoral/issues) to
communicate about work on the project.

There will also be regular [meetup events](https://www.meetup.com/Singapore-Clojure-Meetup/)
that you can attend to discuss or collaborate on the project in person.

## Getting Started

- Fork the repository on GitHub
- Read the [README](README.md) for build and test instructions
- Create or find an existing [GitHub issue](https://github.com/flybot-sg/pastoral/issues)
- Assign the GitHub issue your working on to yourself
- Play with the project, submit pull requests!

## Format of the Commit Message

Commit messages should start with a capitalized and short summary (max. 50 chars)
written in the imperative, followed by an optional, more detailed explanatory
text which is separated from the summary by an empty line.

Commit messages should follow best practices, including explaining the context
of the problem and how it was solved, including in caveats or follow up changes
required. They should tell the story of the change and provide readers
understanding of what led to it.

If you're lost about what this even means, please see [How to Write a Git
Commit Message](http://chris.beams.io/posts/git-commit/) for a start.

In practice, the best approach to maintaining a nice commit message is to
leverage a `git add -p` and `git commit --amend` to formulate a solid
changeset. This allows one to piece together a change, as information becomes
available.

If you squash a series of commits, don't just submit that. Re-write the commit
message, as if the series of commits was a single stroke of brilliance.

That said, there is no requirement to have a single commit for a PR, as long as
each commit tells the story. For example, if there is a feature that requires a
package, it might make sense to have the package in a separate commit then have
a subsequent commit that uses it.
