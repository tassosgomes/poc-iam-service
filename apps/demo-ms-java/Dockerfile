FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Copy module manifests
RUN mkdir -p apps/demo-ms-java libs/sdk-java
COPY apps/demo-ms-java/pom.xml apps/demo-ms-java/pom.xml
COPY libs/sdk-java/pom.xml libs/sdk-java/pom.xml

# Create a minimal parent pom with only the modules required for this image build
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
        <module>libs/sdk-java</module>
        <module>apps/demo-ms-java</module>
    </modules>
</project>
EOF

# Copy sources
COPY apps/demo-ms-java/src apps/demo-ms-java/src
COPY libs/sdk-java/src libs/sdk-java/src

RUN mvn -pl apps/demo-ms-java -am -DskipTests package

# ---- Runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl wget \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /workspace/apps/demo-ms-java/target/demo-ms-java-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
