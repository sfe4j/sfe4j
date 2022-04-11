# SFE4J

Simple File Explorer for spring-boot based Java Web Application.

## Getting Started

These instructions will cover usage information and for the docker container.

### Prerequisities

In order to run this container you'll need docker installed.

* [Windows](https://docs.docker.com/windows/started)
* [OS X](https://docs.docker.com/mac/started/)
* [Linux](https://docs.docker.com/linux/started/)

### Usage

#### Exposed Port

sfe4j/sfe4j expose port at `28080` by default.

```shell
docker run -d -p28080:28080 sfe4j/sfe4j
```

#### Environment Variables

|  key   | allowed value                                                      | default value                                                      | description                                                                  |
|  ----  |--------------------------------------------------------------------|--------------------------------------------------------------------|------------------------------------------------------------------------------|
| TITLE | String                                                             | "SFE4J"                                                            | Title of the file-explorer pag.                                              |
| DESCRIPTION | String                                                             | "Simple File Explorer for spring-boot based Java Web Application." | Description of the file-explorer page.                                       |
| BASE_DIR_PATH | String                                                             | "/"                                                                | Full path of base directory, e.g. "c:/" for Windows, "/" for Linux or MacOS. |
| RESTRICT_TO_BASE_DIR | Boolean | false | Whether restrict the access to base directory only or not.                   |

#### Volumes

* TBD

#### Useful File Locations

* TBD

## Built With

* OpenJDK 17
* Maven 3.8.4

## Find Us

* [GitHub](https://github.com/sfe4j/sfe4j)

## Contributing

Please read [CONTRIBUTING.md](https://github.com/sfe4j/sfe4j#contributing) for details on our code of conduct, and the process for submitting pull requests to us.

## Versioning
-
We use [SemVer](http://semver.org/) for versioning. For the versions available, see the
[tags on this repository](https://github.com/sfe4j/sfe4j/tags).

## Authors

* **N_G** - *Initial work* - [Guuuuo](https://github.com/guuuuo)

See also the list of [contributors](https://github.com/sfe4j/sfe4j/contributors) who
participated in this project.

## License

This project is licensed under the GNU Affero General Public License v3.0 - see the [LICENSE.md](https://github.com/sfe4j/sfe4j/blob/master/LICENSE) file for details.

## Acknowledgments

* TBD