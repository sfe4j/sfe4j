# Build Image
FROM eclipse-temurin:25-jre-alpine
RUN mkdir /opt/app
COPY sfe4j-spring-boot-sample/target/sfe4j-spring-boot-sample.jar /opt/app/app.jar
ENV ACTIVE_PROFILE="prod"
ENTRYPOINT ["java", "-Dspring.profiles.active=${ACTIVE_PROFILE}", "-jar", "/opt/app/app.jar"]
EXPOSE 8080