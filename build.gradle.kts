plugins {
    java
    application
}

group = "Conzy.Server"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("conzy.server.server.GameServer")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.netty:netty-handler:4.1.115.Final")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.1")
    implementation("org.json:json:20240303")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}
