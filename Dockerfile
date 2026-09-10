# Build + runtime da API. Rodar a API em container Linux evita o bug de
# loopback NIO do Windows (Selector.open -> "Unable to establish loopback
# connection") que impede subir o Tomcat direto na máquina.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B -Dmaven.test.skip=true dependency:go-offline
COPY src ./src
RUN mvn -q -B -Dmaven.test.skip=true clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/petconnect-api-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
