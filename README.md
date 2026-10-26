# SFE4J
[![CI Status](https://github.com/sfe4j/sfe4j/actions/workflows/ci.yaml/badge.svg)](https://github.com/sfe4j/sfe4j/actions/workflows/ci.yaml)
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
- Can configure password protection for specific directories
- One authorization path for listing, preview and download; an unauthorized request is always denied
- Text preview is rendered as escaped plain text; file content is never interpreted as markup
- Downloads are streamed with a fixed buffer and a bounded concurrency limit
- More features are coming ...

## Access behaviour
- Listing, preview and download share the same authorization. Directory passwords also protect the
  preview and the download, not only the listing.
- Directory authorization is stored in the session for 30 minutes by default, is invalidated when the
  configuration changes, and can be dropped with the "Exit directory authorization" action without
  destroying the session of the host application.
- Password attempts are limited per session and client address; further attempts are answered with 429.
- The password form is protected by a CSRF token, and the session id is changed after a successful
  authorization.
- Text preview reads at most the configured number of bytes and says so when the content is truncated,
  or when some bytes could not be decoded as UTF-8.
- Downloads use a fixed buffer and never read the whole file into memory. Once the limit of concurrent
  downloads is reached, further requests are answered with 429.
- Errors are reported as a status page or as `{code, message, requestId}` JSON, without leaking host
  paths or exception details.
- A symbolic link between the base directory and the target is rejected by default.
- Path parameters must be absolute or relative to the base directory; the process working directory is
  never used. `.`/`..` are normalized and the ownership of a directory is compared path segment by
  path segment, so `/data2` is not treated as being inside `/data`.

### Intentional behaviour changes

- Preview renders text as escaped plain text; HTML files no longer render.
- An out of range request no longer falls back to the base directory but is answered with a 403 error.
- Preview and download require the same directory authorization as the listing.
- A missing or invalid path parameter is answered with a 400 error instead of a null dereference.
- The base directory and every protected directory must exist at startup; the application refuses to
  start otherwise.
- The container image publishes `/data` instead of the file system root.

## Usage
### Scenario 1. Integrate sfe4j to spring-boot based applications
#### Add repositories
Add the below repository configuration to `pom.xml` of your project or `settings.xml` of Maven.
```xml
<repository>
    <id>sfe4j</id>
    <url>https://raw.github.com/sfe4j/mvn-repo/master</url>
</repository>
```
#### Add dependencies
Add the below dependency configuration to `pom.xml` of your project.
```xml
<dependency>
    <groupId>com.amoylabs</groupId>
    <artifactId>sfe4j-spring-boot-starter</artifactId>
    <version>${sfe4j-spring-boot-starter-version}</version>
</dependency>
```
Latest Version:
```xml
    <sfe4j-spring-boot-starter-version>1.1.0</sfe4j-spring-boot-starter-version>
```
#### Configure properties
##### prefix
```properties
sfe4j
```
##### properties
|  key   | allowed value  | default value | description |
|  ----  | ----  | ---- | ---- |
| title | String | "SFE4J" | Title of the file-explorer page. |
| description | String | "Simple File Explorer for spring-boot based Java Web Application." | Description of the file-explorer page. |
| quick-links | Map | N/A | Quick links showed on top-left of the file-explorer page, e.g. <br> root: "/" <br> logs: "/data/logs"|
| base-dir-path | String | "/" | Full path of base directory, e.g. "c:/" for Windows, "/" for Linux or MacOS. Must be an absolute path that exists and is a directory, otherwise the application refuses to start. |
| restrict-to-base-dir | Boolean | false | Whether restrict the access to base directory only or not. |
| folder-passwords | Map | N/A | Passwords for folder protection. Key is folder path, value is password. Note: use brackets for paths in yaml, e.g. `"[root]": "password"` |
| access.symbolic-link-policy | deny \| within-root | deny | How symbolic links between the base directory and the target are handled. |
| access.grant-ttl-seconds | 60–86400 | 1800 | How long a directory authorization stays valid in the session. |
| access.password-attempts-per-minute | 1–60 | 5 | Password attempts per minute and per session/client address. |
| preview.max-bytes | 1024–16777216 | 1048576 | Maximum number of bytes read for a text preview. |
| download.max-concurrent | 1–128 | 8 | Maximum number of concurrent downloads; further requests are answered with 429. |
##### example
```properties
sfe4j:
  title: "SFE4J Demo"
  description: "Simple File Explorer for spring-boot based Java Web Application."
  quick-links:
    root: "/"
    logs: "/data/logs"
  base-dir-path: "/data"
  restrict-to-base-dir: true
  folder-passwords:
    "[/data/secret]": "my_password"
  access:
    symbolic-link-policy: deny
    grant-ttl-seconds: 1800
    password-attempts-per-minute: 5
  preview:
    max-bytes: 1048576
  download:
    max-concurrent: 8
```
A value outside the documented range stops the application at startup instead of silently falling back
to a default.
#### Entrypoint
```
http://localhost:8080/file-explorer
```

### Scenario 2. Deploy sfe4j to docker
sfe4j was deployed to docker hub. Please refer to: https://hub.docker.com/r/sfe4j/sfe4j
#### Example Command to run sfe4j in docker
```shell
docker run -d -p8080:8080 sfe4j/sfe4j
```
The image publishes the container directory `/data` instead of the whole file system. Mount the
directory to be published, preferably read-only:
```shell
docker run -d -p8080:8080 -v /srv/files:/data:ro sfe4j/sfe4j
```
#### Example URL to access SFE4J
```
http://localhost:8080/file-explorer
```
#### Environment Variables
Please refer to https://hub.docker.com/r/sfe4j/sfe4j for all availabe environment variables.

### Scenario 3. Deploy sfe4j as a sidecar of applications in Kubernetes 
#### Examples
```yaml
      containers:
        - name: sfe4j
          image: sfe4j/sfe4j
          ports:
            - name: http
              containerPort: 8080
          env:
            - name: TITLE
              value: "SFE4J"
            - name: BASE_DIR_PATH
              value: "/data"
```
#### Example URL to access SFE4J
```
http://INGRESS_HOST:INGRESS_PORT/file-explorer
```
#### Environment Variables
Please refer to https://hub.docker.com/r/sfe4j/sfe4j for all availabe environment variables.

## Demo
### Live demo
Link: https://sfe4j-demo.amoylabs.com/file-explorer

### Screenshot
![sfe4j demo screenshot](https://raw.githubusercontent.com/sfe4j/assets-repo/main/sfe4j-demo.png)

## Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

Please make sure to update tests as appropriate.

## License
[GNU Affero General Public License v3.0](https://www.gnu.org/licenses/agpl-3.0.en.html)
