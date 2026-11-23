# Контракт файла результатов, версия 1

## Назначение

Формат файла, который адаптер TestIT пишет на диск ВМ для каждого завершённого теста. Агент-исполнитель на ВМ читает такие файлы, маскирует секреты и персональные данные и передаёт нормализованные результаты в тех.зону (история прогонов). Продюсер (адаптер) и потребители (агент-исполнитель, БД) строятся по этому документу.

## Общие правила

1. **Один файл на тест.** Имя `<uuid>-result.json`, где `uuid` — `TestResult.uuid`. Каталог задаётся настройкой `resultsDir`. Файл пишется один раз, когда тест завершён.
2. **Атомарная запись.** Адаптер пишет во временный файл `<uuid>-result.json.tmp` в том же каталоге и переименовывает его. Потребитель читает только `*-result.json`.
3. **Ошибки записи не должны ломать тесты.** Ошибка записи файла логируется.
4. **Кодировка** UTF-8, без BOM.
5. **Время** — в миллисекундах, UTC (`Long`, как в адаптере). Длительность считает потребитель: `stopMs - startMs`.
6. **Ключи присутствуют всегда.** Вместо `null` для списков пишется `[]`, для словарей `{}`. `null` допустим только у полей, помеченных в колонке «Может быть null».
7. **Статусы** записываются именем enum `ItemStatus.name()`: `PASSED`, `FAILED`, `SKIPPED`, `INPROGRESS`, `BLOCKED`. Тест пишется со статусом до подмены на `BLOCKED` (подмена относится к логике TestIT).
8. **Лимиты объёма**: `failure.trace` и `failure.message` до 20 000 символов, значения параметров до 2 000. При усечении в конец добавляется `…[усечено N симв.]`. Используем `filterStringTrace` из `Converter`.
9. **Версия формата** — поле `schemaVersion`. Добавление необязательных полей версию не меняет. Несовместимое изменение увеличивает версию. Потребитель пропускает файл с неизвестной версией и пишет предупреждение.
10. **Локальность и полнота.** Файл остаётся на ВМ и пишется полностью, независимо от флагов `sendTestResultOnly` и `NO_STEP_PARAMS`, которые ограничивают отправку в TestIT. Что уходит в тех.зону, решает агент-исполнитель по профилю проекта (`restricted`) после маскирования.

## Когда и как пишет адаптер

- Место записи в `AdapterManager.stopTestCase`: **после** `setItemStage(FINISHED).setStop(...)`, **до** выхода по режиму `DISABLED` и **до** подмены статуса на `BLOCKED`.
- Запись не зависит от режима адаптера. Режим 3 (`DISABLED`) означает «результаты формируются, но не отправляются в TestIT», так что файловый режим работает без TestIT.
- Настройка: файл `testit.properties` — `resultsDir`; переменная окружения `TMS_RESULTS_DIR`; системное свойство `tmsResultsDir`. Не задано — файлы не пишутся.

## Пример

```json
{
  "schemaVersion": 1,
  "run": {
    "runId": "310045-regression",
    "testItRunId": null,
    "adapterVersion": "1.2.8",
    "host": "vm-example-01",
    "ci": {
      "pipelineId": "310045",
      "jobId": "4821907",
      "jobName": "regression 2/4",
      "commitSha": "3f2a9c1d7e8b4a5f6c0d1e2f3a4b5c6d7e8f9a0b",
      "ref": "feature/payments",
      "project": "group/project-tests",
      "mergeRequestIid": null,
      "jobUrl": "https://ci.example.invalid/group/project-tests/-/jobs/4821907"
    }
  },
  "test": {
    "uuid": "0b6f7a4e-8a52-4c1b-9d0e-5a3c2f1e7b90",
    "externalId": "111111",
    "workItemIds": ["1234"],
    "namespace": "ru.example.payments",
    "className": "PaymentTest",
    "name": "paymentIsAccepted",
    "title": "Платёж принимается",
    "description": "Проверяется приём платежа на тестовом стенде",
    "status": "FAILED",
    "startMs": 1760000000000,
    "stopMs": 1760000004200,
    "message": "",
    "labels": ["smoke"],
    "links": [
      { "type": "Defect", "title": "BUG-1", "url": "https://tracker.example.invalid/BUG-1" }
    ],
    "parameters": { "stand": "test" },
    "failure": {
      "kind": "ASSERTION",
      "type": "org.opentest4j.AssertionFailedError",
      "message": "expected: <200> but was: <500>",
      "trace": "org.opentest4j.AssertionFailedError: expected: <200> but was: <500>\n\tat ru.example.payments.PaymentTest.paymentIsAccepted(PaymentTest.java:42)",
      "suppressed": []
    },
    "steps": [
      {
        "name": "Открыть форму платежа",
        "description": null,
        "status": "PASSED",
        "startMs": 1760000000100,
        "stopMs": 1760000001200,
        "parameters": {},
        "attachments": [],
        "failure": null,
        "steps": []
      },
      {
        "name": "Отправить платёж",
        "description": "POST /payments",
        "status": "FAILED",
        "startMs": 1760000001200,
        "stopMs": 1760000004100,
        "parameters": { "amount": "100" },
        "attachments": [{ "path": "build/attachments/response.txt" }],
        "failure": {
          "kind": "ASSERTION",
          "type": "org.opentest4j.AssertionFailedError",
          "message": "expected: <200> but was: <500>",
          "trace": "org.opentest4j.AssertionFailedError: expected: <200> but was: <500>",
          "suppressed": []
        },
        "steps": [
          {
            "name": "Проверить код ответа",
            "description": null,
            "status": "FAILED",
            "startMs": 1760000004000,
            "stopMs": 1760000004100,
            "parameters": {},
            "attachments": [],
            "failure": null,
            "steps": []
          }
        ]
      }
    ],
    "attachments": [{ "path": "build/attachments/screenshot.png" }]
  }
}
```

## Поля: корень и блок `run`

| Поле | Тип | Может быть null | Откуда | Примечание |
|---|---|---|---|---|
| `schemaVersion` | число | нет | константа адаптера | сейчас `1` |
| `run.runId` | строка | нет | `MCP_RUN_ID`, иначе `CI_JOB_ID`, иначе `local-<UUID>` | идентификатор прогона платформы, не связан с TestIT. Правила ниже, в разделе «Идентификатор прогона» |
| `run.testItRunId` | строка | да | `clientConfiguration.getTestRunId()` | в адаптере отсутствие записано строкой `"null"`, в файл пишется настоящий `null`. В режиме 2 id появляется после `startTests()` |
| `run.adapterVersion` | строка | нет | атрибут `Implementation-Version` манифеста jar | если нет - `"unknown"` |
| `run.host` | строка | нет | `InetAddress.getLocalHost().getHostName()`, запасной вариант `HOSTNAME` или `COMPUTERNAME`, иначе `"unknown"` | имя машины, на которой шли тесты |
| `run.ci.pipelineId` | строка | да | `CI_PIPELINE_ID` | |
| `run.ci.jobId` | строка | да | `CI_JOB_ID` | |
| `run.ci.jobName` | строка | да | `CI_JOB_NAME` | имя джобы, например `regression 2/4`: показывает, какой части прогона принадлежит тест |
| `run.ci.commitSha` | строка | да | `CI_COMMIT_SHA` | |
| `run.ci.ref` | строка | да | `CI_COMMIT_REF_NAME` | ветка или тег |
| `run.ci.project` | строка | да | `CI_PROJECT_PATH` | |
| `run.ci.mergeRequestIid` | строка | да | `CI_MERGE_REQUEST_IID` | необязательное |
| `run.ci.jobUrl` | строка | да | `CI_JOB_URL` | необязательное |

Правила для `ci`: читается только этот белый список переменных, не всё окружение (в нём бывают токены). Вне CI все поля `null`.

Блок `run` описывает контекст выполнения конкретной JVM, а не весь прогон. Прогон (логический запуск набора, например регресс) может состоять из нескольких частей: джоб CI на разных ВМ, JVM Gradle. Поэтому факты делятся на два уровня.

| Уровень | Поля | Где хранятся в БД |
|---|---|---|
| Прогон (общие для всех частей) | `runId`, `ci.pipelineId`, `ci.commitSha`, `ci.ref`, `ci.project`, `ci.mergeRequestIid` | таблица `run` |
| Часть прогона (джоба, JVM) | `ci.jobId`, `ci.jobUrl`, `ci.jobName`, `host`, `adapterVersion`, `testItRunId` | у результата теста (`test_result`) |

### Идентификатор прогона

Все части одного прогона должны получить одинаковый `runId`, а разные наборы (например, smoke и regression) разные. Адаптер сам ничего не группирует, он берёт идентификатор по порядку:

1. `MCP_RUN_ID` из окружения, если не пусто. Его задаёт тот, кто организует запуск, один раз и общий для всех частей. Это не ручная переменная: формула лежит в конфигурации.
2. Иначе `CI_JOB_ID`: прогон равен джобе.
3. Иначе `local-<UUID>`: ручной локальный запуск вне CI. Значение всегда непустое.

| Сценарий | Откуда `runId` |
|---|---|
| Набор в CI, формула задана | `MCP_RUN_ID`, одинаковый у всех частей |
| Набор в CI, формулы нет | `CI_JOB_ID`, одна запись на джобу |
| Запуск платформой (`run_tests`) | `MCP_RUN_ID`, который выставил агент-исполнитель |
| Узлы диспетчера | `MCP_RUN_ID` от диспетчера |
| Ручной локальный запуск | `local-<UUID>` на JVM |

Формула в CI, одна строка на набор, в `.gitlab-ci.yml` или в общем шаблоне пайплайна:
```yaml
regression:
  variables:
    MCP_RUN_ID: "${CI_PIPELINE_ID}-regression"
smoke:
  variables:
    MCP_RUN_ID: "${CI_PIPELINE_ID}-smoke"
```
Все части матрицы или `parallel` получают одинаковое значение и попадают в один прогон, а smoke остаётся отдельным.

Замечания:
- Gradle при `maxParallelForks` запускает несколько JVM, поэтому ручной локальный запуск без `MCP_RUN_ID` может дать несколько `local-<UUID>`. Для истории это безвредно; один идентификатор на весь запуск гарантирует запуск через агента-исполнителя (он выставляет `MCP_RUN_ID`).
- Перезапуск одной части прогона присылает повторные результаты под тем же `runId`, но с другим `ci.jobId`. Итоговый результат теста в прогоне определяется по наибольшему `ci.jobId`.

## Поля: блок `test`

| Поле | Тип | Может быть null | Откуда | Примечание |
|---|---|---|---|---|
| `uuid` | строка | нет | `TestResult.getUuid()` | уникален на результат |
| `externalId` | строка | нет | `TestResult.getExternalId()` | адаптер берёт из аннотации или генерирует |
| `workItemIds` | массив строк | нет | `TestResult.getWorkItemId()` | геттер называется в единственном числе; может быть `[]` |
| `namespace` | строка | да | `TestResult.getSpaceName()` | |
| `className` | строка | да | `TestResult.getClassName()` | проверить для Cucumber |
| `name` | строка | нет | `TestResult.getName()` | |
| `title` | строка | да | `TestResult.getTitle()` | |
| `description` | строка | да | `TestResult.getDescription()` | |
| `status` | строка | нет | `TestResult.getItemStatus().name()` | значения см. правило 7 |
| `startMs` | число | нет | `TestResult.getStart()` | |
| `stopMs` | число | нет | `TestResult.getStop()` | уже проставлен на момент записи |
| `message` | строка | нет | `TestResult.getMessage()` | |
| `labels` | массив строк | нет | `TestResult.getLabels()` | `Label.getName()` |
| `links` | массив объектов | нет | `TestResult.getLinkItems()` | см. ниже |
| `parameters` | словарь строка→строка | нет | `TestResult.getParameters()` | в адаптере по умолчанию `null`, в файл пишется `{}` |
| `failure` | объект | да | `TestResult.getThrowable()` | `null`, если исключения нет |
| `steps` | массив | нет | `TestResult.getSteps()` | вложенное дерево, см. ниже |
| `attachments` | массив объектов | нет | `TestResult.getLocalAttachments()` (новое поле) | только локальные пути, содержимое в файл не включается. Пути абсолютные и нормализованные (адаптер приводит их в `addAttachments`, потому что относительный путь зависит от рабочего каталога JVM; в примере выше они для краткости относительные). `getAttachments()` хранит идентификаторы вложений в TestIT и в режиме `DISABLED` не заполняется, поэтому для файла нужен отдельный список |

Элемент `links`:

| Поле | Тип | Может быть null | Откуда |
|---|---|---|---|
| `type` | строка | да | `LinkItem.getType().getValue()` |
| `title` | строка | да | `LinkItem.getTitle()` |
| `url` | строка | нет | `LinkItem.getUrl()` |

Элемент `attachments` (в тесте и в шаге одинаковый):

| Поле | Тип | Может быть null | Откуда |
|---|---|---|---|
| `path` | строка | нет | элемент `getLocalAttachments()` |

## Поля: блок `failure` (тест и шаг)

| Поле | Тип | Может быть null | Откуда | Примечание |
|---|---|---|---|---|
| `kind` | строка | нет | вычисляется | `ASSERTION` или `ERROR`, правило ниже |
| `type` | строка | нет | `throwable.getClass().getName()` | полное имя класса исключения. Для исключений `фреймворка` (`ru.autotestframework.core.exception.*`) по типу видно категорию проблемы |
| `message` | строка | да | `throwable.getMessage()` | бывает `null` |
| `trace` | строка | нет | `ExceptionUtils.getStackTrace(throwable)` | включает причины и блоки `Suppressed:`; усечение по правилу 8. Использует `filterStringTrace` из `Converter` |
| `suppressed` | массив `{type, message}` | нет | `throwable.getSuppressed()` | `[]`, если нет. Нужен для агрегатного исключения «Test failed: N step errors» |

Определение `kind`: обойти само исключение, цепочку `getCause()` и массивы `getSuppressed()`, с ограничением глубины и защитой от циклов (множество по идентичности объектов). Если встретился `AssertionError` (в том числе `opentest4j.AssertionFailedError`, ошибки AssertJ и Hamcrest) — `ASSERTION`, иначе `ERROR`. Проверять нужно и `getSuppressed()`, потому что `AdapterManager.checkStepErrors()` собирает ошибки шагов в `RuntimeException` через `addSuppressed` без причины.

## Поля: элемент `steps[]`

| Поле | Тип | Может быть null | Откуда | Примечание |
|---|---|---|---|---|
| `name` | строка | нет | `StepResult.getName()` | |
| `description` | строка | да | `StepResult.getDescription()` | |
| `status` | строка | нет | `StepResult.getItemStatus().name()` | проверить, что у завершённого шага всегда задан |
| `startMs` | число | нет | `StepResult.getStart()` | если не задан, геттер подставляет текущее время |
| `stopMs` | число | нет | `StepResult.getStop()` | то же |
| `parameters` | словарь | нет | `StepResult.getParameters()` | `{}`, если `null` |
| `attachments` | массив объектов | нет | `StepResult.getLocalAttachments()` (новое поле) | как в тесте |
| `failure` | объект | да | `StepResult.getThrowable()` | структура как у `test.failure` |
| `steps` | массив | нет | `StepResult.getSteps()` | рекурсивно, пока шаги не закончатся |

## Вне версии 1

Намеренно не вошло, добавим следующими версиями:

- **Фикстуры** (`ClassContainer`, `MainContainer`, `FixtureResult`): падения в `@BeforeAll` и `@AfterAll`, типичная «инфраструктура». В режиме `DISABLED` адаптер их сейчас пропускает целиком, это надо учесть.
- **Номер попытки.** Повторы одного теста в рамках прогона сейчас различаются только `uuid` и временем.
- `TestResult.resultLinks`, `itemStage`, ссылки шагов (`StepResult.linkItems`).
- Содержимое вложений.

## Журнал изменений

- v1, 2026-10-19: первая версия.
- v1, 2026-10-19: источник вложений уточнён: вместо `getAttachments()` (идентификаторы TestIT) используется новый список локальных путей `getLocalAttachments()`. Формат файла не изменился.
- v1, 2026-10-20: пути вложений записываются абсолютными и нормализованными.
- v1, 2026-10-21: добавлено пояснение, что `host`, `adapterVersion` и `testItRunId` относятся к JVM, а не к прогону. Формат файла не изменился.
- v1, 2026-10-21: правила `runId` (`MCP_RUN_ID`, `CI_JOB_ID`, `local-<UUID>`, значение всегда непустое), поле `ci.jobName`, раздел «Идентификатор прогона», два уровня фактов (прогон и часть прогона).
