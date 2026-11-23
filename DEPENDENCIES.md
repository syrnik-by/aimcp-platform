# Зависимости

Правило: версия не новее 20.02.2022 (включая транзитивные). Версии фиксируем явно, без диапазонов. Перед подключением называется точная версия и дата выпуска, добавление — после подтверждения пользователя. Даты берутся с Maven Central (поле Last-Modified файла .pom).

## Подключено

| Библиотека | Версия | Дата выпуска | Зачем | Модуль | Проверено |
|---|---|---|---|---|---|
| Плагин `org.springframework.boot` | 2.6.3 | 2022-01-20 | `bootRun` и `bootJar` | techzone | 2026-10-12 |
| `spring-boot-dependencies` (BOM, через `platform`) | 2.6.3 | 2022-01-20 | единые версии транзитивных зависимостей | techzone | 2026-10-12 |
| `spring-boot-starter-web` | 2.6.3 | 2022-01-20 | HTTP-сервер (Tomcat), Jackson, Logback, чтение `application.yaml` | techzone | 2026-10-12 |
| `junit-jupiter` (явная версия `junitVersion`) | 5.8.2 | 2021-11-28 | тесты | все модули (testImplementation) | 2026-10-13 |
| `assertj-core` (явная версия `assertjVersion`) | 3.21.0 | 2021-09-19 | утверждения в тестах | все модули (testImplementation) | 2026-10-13 |

Версии `junitVersion` и `assertjVersion` держим равными версиям из BOM Spring Boot 2.6.3, иначе версии в `techzone` разойдутся.

Mockito разрешён правилами (2026-10-12), версию назовём и проверим по дате при первом подключении.

## Подключено в шаге 1.7 (JDBC-адаптер истории, adapters-postgres)

Подтверждено пользователем 2026-10-27, подключено 2026-10-28.

Проверено 2026-10-27: версии из BOM Spring Boot 2.6.3, Testcontainers вне BOM. Всего 25 артефактов (с транзитивными, `compile`/`runtime`/тестовые classpath), самая поздняя дата — 2022-01-18 (Testcontainers 1.16.3; с D-028 снята, см. «Снято с проекта»).

| Библиотека | Версия | Дата выпуска | Зачем | Модуль |
|---|---|---|---|---|
| `spring-jdbc` (+ `spring-tx`, `spring-beans`, `spring-core`, `spring-jcl` 5.3.15) | 5.3.15 | 2022-01-13 | `JdbcTemplate`, `TransactionTemplate` | adapters-postgres |
| `postgresql` (+ `checker-qual` 3.5.0) | 42.3.1 | 2021-10-29 | JDBC-драйвер (`runtimeOnly`) | adapters-postgres |
| `jackson-databind` (+ `-core`, `-annotations` 2.13.1) | 2.13.1 | 2021-12-19 | JSON для колонок `jsonb` | adapters-postgres |

## Подключено в шаге 1.8 (приём результатов в тех.зоне)

Подтверждено пользователем 2026-10-28, подключено 2026-10-28 (`testImplementation` в `techzone/build.gradle`).

Проверено 2026-10-28: версия из BOM Spring Boot 2.6.3. Новых артефактов 27 (поверх `starter-web`), самая поздняя дата — 2022-01-20.

| Библиотека | Версия | Дата выпуска | Зачем | Модуль |
|---|---|---|---|---|
| `spring-boot-starter-test` (+ `spring-boot-test`, `spring-test` 5.3.15, Mockito 4.0.0, `byte-buddy` 1.11.22, `json-path` 2.6.0, `jsonassert` 1.5.0, `xmlunit-core` 2.8.4, `hamcrest` 2.2 и др.) | 2.6.3 | 2022-01-20 | тест HTTP-слоя (`MockMvc`) | techzone (test) |

## Подключено в шаге 1.9 (Postgres в тех.зоне)

Подтверждено пользователем 2026-10-29 (внесено в `techzone/build.gradle`), подключено 2026-10-29. Проверено 2026-10-28 (даты .pom на Maven Central). Версии HikariCP и Flyway совпадают с BOM Spring Boot 2.6.3.

| Библиотека | Версия | Дата выпуска | Зачем | Модуль |
|---|---|---|---|---|
| `com.zaxxer:HikariCP` (из транзитивных только `slf4j-api`, он уже есть) | 4.0.3 | 2021-03-03 | пул соединений с Postgres; `DataSource` собираем вручную из `HikariConfig`, без автонастройки Spring Boot | techzone |

Не подключается: Flyway 8.0.5 и собственный запуск миграций отвергнуты (D-026), в контуре схему создаёт пользователь вручную из файлов `V*.sql`.

## Снято с проекта

Testcontainers 1.16.3 (`org.testcontainers:postgresql` и транзитивные: `testcontainers`, `jdbc`, `database-commons`, `docker-java` 3.2.12, `commons-compress` 1.21, `jna` 5.8.0, `junit:junit` 4.13.2 и др.) подключалась в шагах 1.7 и 1.9 для тестов `adapters-postgres` и `techzone`. Снята 2026-10-29 (D-028): на корпоративной машине Docker в обычном пользовании нет, тесты проекта не должны от него зависеть. Свойства `testcontainersVersion` и `testPostgresImage` удалены из `gradle.properties`.

## Шаг 2.1 (профиль проекта, runner)

Подтверждено пользователем 2026-10-30, подключено в `runner/build.gradle` (строку внёс пользователь), `implementation`. Транзитивных зависимостей нет.

Проверено 2026-10-29. Версия из BOM Spring Boot 2.6.3; уже входит в дерево зависимостей `techzone` (читает `application.yaml`), в `runner` нужна явная строка.

| Библиотека | Версия | Дата выпуска | Зачем | Модуль |
|---|---|---|---|---|
| `org.yaml:snakeyaml` (без транзитивных) | 1.29 | 2021-06-08 | чтение `at-profile.yaml` | runner |

## Подключено в подшаге 2.3.1 (HTTP-сервер агента-исполнителя, runner)

Подтверждено пользователем 2026-11-12, подключено 2026-11-12 (строки внёс пользователь в `runner/build.gradle`). Проверено 2026-11-12 по `runtimeClasspath`: новых артефактов 7, самая поздняя дата — 2022-01-13 (`slf4j-api` 1.7.33), новее границы 20.02.2022 ничего нет.

Проверено 2026-11-11 (даты .pom на Maven Central). Версии из BOM Spring Boot 2.6.3, в `runner` нужны явные строки и `platform` BOM. Транзитивные: `jackson-core`, `jackson-annotations`, `jackson-bom` (2.13.1, 2021-12-19), `logback-core` (1.2.10, 2021-12-23), `slf4j-api` (1.7.33, 2022-01-13) — все уже в дереве `techzone`. `logback-classic` запрашивает `slf4j-api` 1.7.32 (2021-07-20), Gradle поднимает до 1.7.33.

| Библиотека | Версия | Дата выпуска | Зачем | Модуль |
|---|---|---|---|---|
| `com.fasterxml.jackson.core:jackson-databind` (+ `-core`, `-annotations`) | 2.13.1 | 2021-12-19 | JSON запросов и ответов (подтверждена в шаге 1.7 для adapters-postgres) | runner |
| `org.slf4j:slf4j-api` | 1.7.33 | 2022-01-13 | журнал агента | runner |
| `ch.qos.logback:logback-classic` (+ `logback-core` 1.2.10, 2021-12-23) | 1.2.10 | 2021-12-23 | вывод журнала | runner |

Вариант «сделать руками» для журнала: `System.Logger` из JDK без зависимостей.

## Проверка транзитивных зависимостей

2026-10-12, шаг 0.3: проверены 48 артефактов (`runtimeClasspath`, `compileClasspath`, `buildEnvironment` модуля techzone). Самая поздняя дата — 2022-01-20 (Spring Boot 2.6.3). Артефактов новее 2022-02-20 нет.
Ключевые версии из BOM: Jackson 2.13.1, Logback 1.2.10, SLF4J 1.7.33, Spring Framework 5.3.15, Tomcat 9.0.56, SnakeYAML 1.29.

2026-10-13, шаг 0.4: проверена тестовая цепочка (`core:testRuntimeClasspath`, 9 артефактов: JUnit Jupiter 5.8.2, JUnit Platform 1.8.2, opentest4j 1.2.0, AssertJ 3.21.0). Самая поздняя дата — 2021-11-28.
Граница 2022-02-20 проверяется вручную при подключении каждой зависимости: `dependencies` по runtime, compile, test и `buildEnvironment`, затем дата .pom на Maven Central. Автоматической задачи в сборке нет (см. D-014).

## Образы Docker (не подпадают под правило даты)

| Образ | Версия | Что внутри | Зачем | Проверено |
|---|---|---|---|---|
| `pgvector/pgvector` | 0.7.4-pg15 | PostgreSQL 15.7, pgvector 0.7.4 | история прогонов и векторный поиск | 2026-10-13 |
