# Build Image
FROM eclipse-temurin:17-jre-alpine
RUN mkdir /opt/app
COPY sfe4j-spring-boot-sample/target/sfe4j-spring-boot-sample.jar /opt/app/app.jar
ENTRYPOINT ["java","-jar","/opt/app/app.jar"]