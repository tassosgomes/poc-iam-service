FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

RUN mkdir -p apps/authz-service
COPY pom.xml apps/authz-service/pom.xml

RUN cat <<'EOF' > pom.xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.platform.authz</groupId>
    <artifactId>authz-platform-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.release>${java.version}</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring-boot.version>3.3.0</spring-boot.version>
    </properties>

    <modules>
        <module>apps/authz-service</module>
    </modules>
</project>
EOF

COPY src apps/authz-service/src

RUN mvn -pl apps/authz-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /workspace/apps/authz-service/target/authz-service-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]