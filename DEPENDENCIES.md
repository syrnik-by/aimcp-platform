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

## Проверка транзитивных зависимостей

2026-10-12, шаг 0.3: проверены 48 артефактов (`runtimeClasspath`, `compileClasspath`, `buildEnvironment` модуля techzone). Самая поздняя дата — 2022-01-20 (Spring Boot 2.6.3). Артефактов новее 2022-02-20 нет.
Ключевые версии из BOM: Jackson 2.13.1, Logback 1.2.10, SLF4J 1.7.33, Spring Framework 5.3.15, Tomcat 9.0.56, SnakeYAML 1.29.

2026-10-13, шаг 0.4: проверена тестовая цепочка (`core:testRuntimeClasspath`, 9 артефактов: JUnit Jupiter 5.8.2, JUnit Platform 1.8.2, opentest4j 1.2.0, AssertJ 3.21.0). Самая поздняя дата — 2021-11-28.
Граница 2022-02-20 проверяется вручную при подключении каждой зависимости: `dependencies` по runtime, compile, test и `buildEnvironment`, затем дата .pom на Maven Central. Автоматической задачи в сборке нет (см. D-014).
