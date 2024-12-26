# Api-Doc-Crafter

Api-Doc-Merger is a standalone tool designed to merge multiple API files and generate Swagger HTML documentation. It
also cleans and filters API files by ignoring invalid entries and removing servers and paths based on glob patterns.
This makes it particularly useful for generating documentation from code and excluding endpoints tagged as internal.

[![](https://img.shields.io/static/v1?label=Sponsor&message=%E2%9D%A4&logo=GitHub&color=%23fe8e86)](https://github.com/sponsors/YunaBraska)

[![Build][build_shield]][build_link]
[![Maintainable][maintainable_shield]][maintainable_link]
[![Coverage][coverage_shield]][coverage_link]
[![Issues][issues_shield]][issues_link]
[![Commit][commit_shield]][commit_link]
[![License][license_shield]][license_link]
[![Tag][tag_shield]][tag_link]
[![Size][size_shield]][size_shield]
![Label][label_shield]
![Label][node_version]
[![Licenses](https://img.shields.io/badge/Licenses-065d7c?style=flat-square)](https://github.com/YunaBraska/api-doc-crafter/blob/main/dist/licenses.txt)

### Features

* **Merge API Files**: Combines multiple OpenAPI files into a single comprehensive document.
* **Generate Swagger HTML**: Produces Swagger HTML documentation from the merged API files.
* **Clean API Files**: Automatically ignores invalid entries in the API files.
* **Filter by Glob Patterns**: Removes servers and paths based on specified glob patterns, allowing for the exclusion of
  internal endpoints.
* **Standalone Operation**: No external dependencies required, making it easy to integrate into any workflow.

## Usage

```yaml
# RUNNER
-   name: "Merge Api Files"
    uses: YunaBraska/api-doc-crafter@main

    # CONFIGS (Optional)
    with:
        deep: '-1'
        work-dir: '.'
```

* Hint for multi-modules: The highest java version will win the race.

### Inputs

| parameter | default | description                      |
|-----------|---------|----------------------------------|
| work-dir  | '.'     | folder scan ('.' == current)     |
| deep      | -1      | folder scan deep (-1 == endless) |

### \[DEV] Setup Environment

* Use Java >= 21
* Build Project: `./mvnw clean package` to compile the project
* Build Docker (Optional): `docker build -t app -f Dockerfile .`

[build_shield]: https://github.com/YunaBraska/api-doc-crafter/workflows/RELEASE/badge.svg

[build_link]: https://github.com/YunaBraska/api-doc-crafter/actions/workflows/publish.yml/badge.svg

[maintainable_shield]: https://img.shields.io/codeclimate/maintainability/YunaBraska/api-doc-crafter?style=flat-square

[maintainable_link]: https://codeclimate.com/github/YunaBraska/api-doc-crafter/maintainability

[coverage_shield]: https://img.shields.io/codeclimate/coverage/YunaBraska/api-doc-crafter?style=flat-square

[coverage_link]: https://codeclimate.com/github/YunaBraska/api-doc-crafter/test_coverage

[issues_shield]: https://img.shields.io/github/issues/YunaBraska/api-doc-crafter?style=flat-square

[issues_link]: https://github.com/YunaBraska/api-doc-crafter/commits/main

[commit_shield]: https://img.shields.io/github/last-commit/YunaBraska/api-doc-crafter?style=flat-square

[commit_link]: https://github.com/YunaBraska/api-doc-crafter/issues

[license_shield]: https://img.shields.io/github/license/YunaBraska/api-doc-crafter?style=flat-square

[license_link]: https://github.com/YunaBraska/api-doc-crafter/blob/main/LICENSE

[tag_shield]: https://img.shields.io/github/v/tag/YunaBraska/api-doc-crafter?style=flat-square

[tag_link]: https://github.com/YunaBraska/api-doc-crafter/releases

[size_shield]: https://img.shields.io/github/repo-size/YunaBraska/api-doc-crafter?style=flat-square

[label_shield]: https://img.shields.io/badge/Yuna-QueenInside-blueviolet?style=flat-square

[gitter_shield]: https://img.shields.io/gitter/room/YunaBraska/api-doc-crafter?style=flat-square

[gitter_link]: https://gitter.im/api-doc-crafter/Lobby

[node_version]: https://img.shields.io/badge/node-16-blueviolet?style=flat-square
