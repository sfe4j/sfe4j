# SFE4J

[![Java CI with Maven](https://github.com/sfe4j/sfe4j/actions/workflows/java-maven.yml/badge.svg)](https://github.com/sfe4j/sfe4j/actions/workflows/java-maven.yml)
[![License](https://img.shields.io/github/license/sfe4j/sfe4j?logo=gnu)](https://github.com/sfe4j/sfe4j/blob/master/LICENSE)

Simple File Explorer for spring-boot based Java Web Application.

## Features

- List the directories/files in a specific directory, include detailed information:
  * Permission
  * Size
  * Date Modified
- Supported Actions:
  * View text/logs/html online
  * Download files
- Can specific the default directory
- Can restrict the access to a specified directory
- Can configure title/description of the page
- Can configure quick links  
- More features are coming ...

## Usage
### Add repositories
Add the below repository configuration to `pom.xml` of your project or `settings.xml` of Maven.
```xml
<repository>
    <id>sfe4j</id>
    <url>https://raw.github.com/sfe4j/mvn-repo/master</url>
</repository>
```

### Add dependencies
Add the below dependency configuration to `pom.xml` of your project.
```xml
<dependency>
    <groupId>com.amoylabs</groupId>
    <artifactId>sfe4j-spring-boot-starter</artifactId>
    <version>${sfe4j-spring-boot-starter-version}</version>
</dependency>
```

### Configure properties
#### prefix
```properties
sfe4j
```

#### properties
|  key   | allowed value  | default value | description |
|  ----  | ----  | ---- | ---- |
| title | String | "SFE4J" | Title of the file-explorer page. |
| description | String | "Simple File Explorer for spring-boot based Java Web Application." | Description of the file-explorer page. |
| quick-links | Map | N/A | Quick links showed on top-left of the file-explorer page, e.g. <br> root: "/" <br> logs: "/data/logs"|
| base-dir-path | String | "/" | Full path of base directory, e.g. "c:/" for Windows, "/" for Linux or MacOS. |
| restrict-to-base-dir | Boolean | false | Whether restrict the access to base directory only or not. |

#### example
```properties
sfe4j:
  title: "SFE4J Demo"
  description: "Simple File Explorer for spring-boot based Java Web Application."
  quick-links:
    root: "/"
    logs: "/data/logs"
  base-dir-path: "/data"
  restrict-to-base-dir: true
```

## Demo
### Live demo
Link: https://backoffice.amoylabs.com:8443/file-explorer

### Screenshot
![sfe4j demo screenshot](https://raw.githubusercontent.com/sfe4j/assets-repo/main/sfe4j-demo.png)

## Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

Please make sure to update tests as appropriate.

## License
[GNU Affero General Public License v3.0](https://www.gnu.org/licenses/agpl-3.0.en.html)
