# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [unreleased]
### Added
- New syntax for memory allocation, references and reference operations.
- Improved output when internal errors occur while parsing.
- Multi-assignments without the need for parentheses.

### Changed
- Improved error messages in parser.
- Syntax of control flow instructions.
- Error message when finalizing non-initialized variable.

## [0.1.0] - 2023-09-09

### Added

- Proper support for unit types and unit values.
- Proper README with explanation of constructs and philosophy.

### Fixed

- Missing spaces in parser error messages.

### Changed

- Internal representation of assignments without arithmetic modifier.
- Descriptions of tokens in parser error messages.

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
