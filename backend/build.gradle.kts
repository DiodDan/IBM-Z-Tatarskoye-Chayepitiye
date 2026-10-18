plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    kotlin("plugin.jpa") version "2.2.21"
    id("org.springframework.boot") version "3.5.15"
    id("io.spring.dependency-management") version "1.1.7"
    java
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

// The standalone model verifier also has a main method; keep Spring startup explicit.
springBoot {
    mainClass.set("com.example.conversation.ConversationApplicationKt")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        javaParameters.set(true)
    }
}

val nativeOrtJar = providers.gradleProperty("ortJar")
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:1.1.8")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    if (nativeOrtJar.isPresent) implementation(files(nativeOrtJar.get()))
    else implementation("com.microsoft.onnxruntime:onnxruntime:1.23.2")
    implementation("ai.djl.huggingface:tokenizers:0.36.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.wrapper {
    gradleVersion = "8.14.3"
    distributionType = Wrapper.DistributionType.BIN
}

// Runs compiled classes directly; no library packaging/publication step.
tasks.register<JavaExec>("verifyModel") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.example.conversation.trajectory.ModelVerification")
    systemProperty("trajectory.verificationReport", layout.buildDirectory.file("reports/trajectory/verification.json").get().asFile.absolutePath)
    systemProperty("trajectory.requireS390x", providers.gradleProperty("requireS390x").orElse("false").get())
    val bundle = providers.gradleProperty("bundle").orElse("src/main/resources/trajectory")
    args(file(bundle.get()).absolutePath, providers.gradleProperty("threads").orElse("3").get())
    if (providers.gradleProperty("verifyResources").isPresent) {
        classpath += files(providers.gradleProperty("resourcesJar").map { file(it) }.getOrElse(file(bundle.get()).parentFile))
        systemProperty("trajectory.verifyResources", "true")
    }
}

tasks.bootJar {
    archiveFileName.set("app.jar")
}

tasks.jar { enabled = false }