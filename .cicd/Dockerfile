# Build Image
FROM eclipse-temurin:17-jre-alpine
RUN mkdir /opt/app
COPY sfe4j-spring-boot-sample/target/sfe4j-spring-boot-sample.jar /opt/app/app.jar
ENV ACTIVE_PROFILE="prod" \
    BASE_DIR_PATH="/data" \
    RESTRICT_TO_BASE_DIR="false" \
    TITLE="SFE4J" \
    DESCRIPTION="Simple File Explorer for spring-boot based Java Web Application."
ENTRYPOINT ["java", "-Dspring.profiles.active=${ACTIVE_PROFILE}", "-jar", "/opt/app/app.jar"]
EXPOSE 28080