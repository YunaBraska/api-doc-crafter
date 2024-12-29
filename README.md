# Api-Doc-Crafter

Api-Doc-Merger is a standalone tool designed to merge multiple API files and generate Swagger HTML documentation. It
also cleans and filters API files by ignoring invalid entries and removing servers and paths based on glob patterns.
This makes it particularly useful for generating documentation from code and excluding endpoints tagged as internal.

[![](https://img.shields.io/static/v1?label=Sponsor&message=%E2%9D%A4&logo=GitHub&color=%23fe8e86)](https://github.com/sponsors/YunaBraska)

[![Build][build_shield]][build_link]
![c_java_version](https://img.shields.io/badge/java_version-21-97CA00?style=flat-square)
![c_builder_name](https://img.shields.io/badge/builder_name-Maven-97CA00?style=flat-square)
[![Issues][issues_shield]][issues_link]
[![Commit][commit_shield]][commit_link]
[![License][license_shield]][license_link]
[![Tag][tag_shield]][tag_link]
[![Size][size_shield]][size_shield]
![Label][label_shield]
![Label][node_version]
[![Licenses](https://img.shields.io/badge/Licenses-065d7c?style=flat-square)](https://github.com/YunaBraska/api-doc-crafter/blob/main/dist/licenses.txt)
[![c_license_count](https://img.shields.io/badge/licenses-0-4c1?style=flat-square)](docs/licenses/licenses.csv)
[![c_license_count_limited](https://img.shields.io/badge/licenses_limited-0-4c1?style=flat-square)](docs/licenses/licenses.csv)
[![c_dependency_count](https://img.shields.io/badge/dependencies-0-4c1?style=flat-square)](docs/licenses/dependencies.csv)

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

_GitHub mounts the current workdir as `/github/workspace` and the output directory is `/github/workspace/swagger_output`
to the docker image.
All inputs are parsed as environment variables which are mapped in the [action.yml](action.yml) file. The App reads envs and args._

## Inputs

_for NON GitHub Action usage: use prefix `adc_` for all input parameters_

| Parameter           | type    | Default                                                                   | Description                                                                                                         |
|---------------------|---------|---------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `swagger_js`        | Path    | https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui-standalone-preset.js  | \[UI] (local=no external resource) Path to the Swagger JS file.                                                     |
| `swagger_bundle_js` | Path    | https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui-bundle.js             | \[UI] (local=no external resource) Path to the Swagger JS file.                                                     |
| `swagger_css`       | Path    | https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui.css                   | \[UI] (local=no external resource) Path to the Swagger CSS file.                                                    |
| `swagger_nav_css`   | Path    | none                                                                      | \[UI] (local=no external resource) Path to the Swagger CSS file.                                                    |
| `swagger_favicon`   | Path    | https://petstore.swagger.io/favicon-32x32.png                             | \[UI] (local=no external resource) Path to the Swagger favicon png file.                                            |
| `swagger_logo`      | Path    | https://static1.smartbear.co/swagger/media/assets/images/swagger_logo.svg | \[UI] (local=no external resource) Path to the Swagger logo file.                                                   |
| `swagger_nav`       | Boolean | true                                                                      | \[UI] Generate navigation                                                                                           |
| `swagger_links`     | Boolean | true                                                                      | \[UI] Generate download source links                                                                                |
| `work_dir`          | Path    | `.`                                                                       | Working directory for the input files.                                                                              |
| `output_dir`        | Path    | `./swagger_output`                                                        | Directory to save the output files.                                                                                 |
| `file_includes`     | Glob    |                                                                           | File patterns to include. GLOB patterns separated by '::' or '\|'.                                                  |
| `file_excludes`     | Glob    |                                                                           | File patterns to exclude. GLOB patterns separated by '::' or '\|'.                                                  |
| `max_deep`          | Integer | `100`                                                                     | Maximum directory depth to search.                                                                                  |
| `sort_extensions`   | Boolean | true                                                                      | \[Sort] (none=disable, true=asc, false=desc) OpenAPI extensions.                                                    |
| `sort_servers`      | Boolean | true                                                                      | \[Sort] (none=disable, true=asc, false=desc) OpenAPI servers.                                                       |
| `sort_security`     | Boolean | true                                                                      | \[Sort] (none=disable, true=asc, false=desc) OpenAPI security schemes.                                              |
| `sort_tags`         | Boolean | true                                                                      | \[Sort] (none=disable, true=asc, false=desc) OpenAPI tags.                                                          |
| `sort_paths`        | Boolean | true                                                                      | \[Sort] (none=disable, true=asc, false=desc) OpenAPI paths.                                                         |
| `sort_schemas`      | Boolean | true                                                                      | \[Sort] (none=disable, true=asc, false=desc) OpenAPI schemas.                                                       |
| `sort_parameters`   | Boolean | true                                                                      | \[Sort] (none=disable, true=asc, false=desc) OpenAPI parameters.                                                    |
| `sort_responses`    | Boolean | true                                                                      | \[Sort] (none=disable, true=asc, false=desc) OpenAPI responses.                                                     |
| `sort_examples`     | Boolean | true                                                                      | \[Sort] (none=disable, true=asc, false=desc) OpenAPI examples.                                                      |
| `sort_webhooks`     | Boolean | true                                                                      | \[Sort] (none=disable, true=asc, false=desc) OpenAPI webhooks.                                                      |
| `sort_headers`      | Boolean | true                                                                      | \[Sort] (none=disable, true=asc, false=desc) OpenAPI headers.                                                       |
| `sort_scopes`       | Boolean | true                                                                      | \[Sort] (none=disable, true=asc, false=desc) OpenAPI scopes.                                                        |
| `sort_requests`     | Boolean | true                                                                      | \[Sort] (none=disable, true=asc, false=desc) OpenAPI requests.                                                      |
| `sort_content`      | Boolean | true                                                                      | \[Sort] (none=disable, true=asc, false=desc) OpenAPI content.                                                       |
| `sort_encoding`     | Boolean | true                                                                      | \[Sort] (none=disable, true=asc, false=desc) OpenAPI encodings.                                                     |
| `remove_key`        | Glob    | null                                                                      | Keys or fields to remove from OpenAPI files. Removes also servers or paths by their identifiers e.g. url, tags,...  |
| `group_tags`        | Glob    | null                                                                      | Group OpenAPI files by tags. Separate tags with '::', '\|', or ','.  (disabling can be done by non matching values) |
| `group_servers`     | Glob    | null                                                                      | Group OpenAPI files by servers. Separate servers with '::' or '\|'.  (disabling can be done by non matching values) |

## Known Issues

* Parsing files without `"openapi":"3.0.1"` property could be ignored by the parser when running as binary in
  docker/github action. Reason=unkown

## \[DEV] Setup Environment

* Use Java >= 21
* Build Project: `./mvnw clean package` to compile the project
* Build Docker (Optional): `docker build -t app -f Dockerfile .`
* `AppTest.java` or `mvn test` will generate an example html page with files from `src/test/resources/files`
* [bin/static](src/main/resources/bin/static) is a special folder which will be included in the executable binary
* [ReflectionConfigGeneratorTest.java](src/test/java/berlin/yuna/apidoccrafter/logic/ReflectionConfigGeneratorTest.java)
  will generate
  a [reflection-config.json](src/main/resources/META-INF/native-image/berlin.yuna/api-doc-crafter/reflect-config.json)
  file for GraalVM native image, as the swagger parser uses sadly reflection

### Docker files

* Dockerfile:
    * Description: Used as executable in `action.yml` to build the application from the releases
    * Build `docker build -t app -f Dockerfile .`
    * Build Arch: `docker buildx build --platform linux/amd64 -f Dockerfile .`
* Dockerfile_App:
    * Description: Unused alternative to build app files using jlink and jpackage
    * Build `docker build -t app -f Dockerfile_App .`
    * Build Arch: `docker buildx build --platform linux/amd64 -f Dockerfile_App .`
    * Extract file: `docker buildx build --platform linux/amd64 -f Dockerfile_App --target export . --output target`
* Dockerfile_Native:
    * Description: Used in pipeline to build and release binaries using graalvm
    * Build `docker build -t app -f Dockerfile_Native .`
    * Build Arch: `docker buildx build --platform linux/amd64 -f Dockerfile_Native .`
    * Extract file: `docker buildx build --platform linux/amd64 -f Dockerfile_Native --target export . --output target`

### Deployment

Everything is automated using GitHub Actions

* [java_build.yml](.github/workflows/java_build.yml) updates versions &
  triggers [github_release.yml](.github/workflows/github_release.yml)
* [github_release.yml](.github/workflows/github_release.yml) builds & releases native executables
  using [Dockerfile_Native.yml](Dockerfile_Native)
    * Graalvm only supports AMD64, ARM64
* [github_docker.yml](.github/workflows/github_docker.yml) builds multi arch docker image with released binaries, after
  a new release is created

[build_shield]: https://github.com/YunaBraska/api-doc-crafter/actions/workflows/test_workflow.yml/badge.svg

[build_link]: https://github.com/europace/api-doc-crafter/actions/workflows/test_workflow.yml

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
