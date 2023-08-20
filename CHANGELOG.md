# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [indev-2] - 2023-08-20

### Added

- Structure literals to create structures without manually implemented constructors.
- An instruction supporting the assignment of multiple resources at once.
- Improved error messages for conflicts detected during liveness analysis.
- Improved type error messages.

### Fixed

- Bug in liveness analysis where variables of a structure type could not be initialized or finalized.

## [indev-1] - 2023-08-06

### Added

- First public release.
