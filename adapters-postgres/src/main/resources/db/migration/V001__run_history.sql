CREATE TABLE run
(
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project              text        NOT NULL,
    run_id               text        NOT NULL,
    ci_pipeline_id       text,
    ci_commit_sha        text,
    ci_ref               text,
    ci_project_path      text,
    ci_merge_request_iid text,
    started_at           timestamptz,
    finished_at          timestamptz,
    received_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT run_project_run_id_key UNIQUE (project, run_id)
);

COMMENT ON TABLE run IS 'Прогон: логический запуск набора тестов проекта (например, регресс), который может состоять из нескольких частей: джоб CI на разных ВМ';
COMMENT ON COLUMN run.id IS 'Суррогатный ключ прогона';
COMMENT ON COLUMN run.project IS 'Идентификатор проекта из профиля проекта; задаётся тем, кто загружает результаты';
COMMENT ON COLUMN run.run_id IS 'Идентификатор прогона: MCP_RUN_ID, иначе CI_JOB_ID, иначе local-<UUID>. Одинаков у всех частей прогона, уникален вместе с project';
COMMENT ON COLUMN run.ci_pipeline_id IS 'Идентификатор пайплайна в ahcode (CI_PIPELINE_ID); объединяет джобы одного пайплайна';
COMMENT ON COLUMN run.ci_commit_sha IS 'SHA коммита, на котором выполнялся прогон (CI_COMMIT_SHA)';
COMMENT ON COLUMN run.ci_ref IS 'Ветка или тег, для которых запущен пайплайн (CI_COMMIT_REF_NAME)';
COMMENT ON COLUMN run.ci_project_path IS 'Путь проекта в ahcode: группа/проект (CI_PROJECT_PATH)';
COMMENT ON COLUMN run.ci_merge_request_iid IS 'Номер merge request внутри проекта (CI_MERGE_REQUEST_IID); пусто, если пайплайн запущен не для MR';
COMMENT ON COLUMN run.started_at IS 'Время начала самого раннего теста прогона';
COMMENT ON COLUMN run.finished_at IS 'Время окончания самого позднего теста прогона';
COMMENT ON COLUMN run.received_at IS 'Когда тех.зона получила результаты прогона';

CREATE TABLE test_result
(
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_pk             bigint   NOT NULL REFERENCES run (id) ON DELETE CASCADE,
    tms_run_id         text,
    adapter_version    text     NOT NULL,
    host               text     NOT NULL,
    ci_job_id          text,
    ci_job_url         text,
    ci_job_name        text,
    schema_version     smallint NOT NULL,
    uuid               text     NOT NULL,
    external_id        text     NOT NULL,
    work_item_ids      text[]   NOT NULL DEFAULT '{}',
    namespace          text,
    class_name         text,
    name               text     NOT NULL,
    title              text,
    description        text,
    message            text,
    status             text     NOT NULL CHECK (status IN ('PASSED', 'FAILED', 'SKIPPED', 'INPROGRESS', 'BLOCKED')),
    started_at         timestamptz,
    stopped_at         timestamptz,
    duration_ms        bigint GENERATED ALWAYS AS ((extract(epoch FROM (stopped_at - started_at)) * 1000)::bigint) STORED,
    labels             text[]   NOT NULL DEFAULT '{}',
    links              jsonb    NOT NULL DEFAULT '[]',
    parameters         jsonb    NOT NULL DEFAULT '{}',
    steps              jsonb    NOT NULL DEFAULT '[]',
    attachments        jsonb    NOT NULL DEFAULT '[]',
    failure_kind       text CHECK (failure_kind IN ('ASSERTION', 'ERROR')),
    failure_type       text,
    failure_message    text,
    failure_trace      text,
    failure_suppressed jsonb    NOT NULL DEFAULT '[]',
    CONSTRAINT test_result_run_pk_uuid_key UNIQUE (run_pk, uuid)
);

COMMENT ON TABLE test_result IS 'Результат одного теста в прогоне: статус, ошибка, шаги и привязка к части прогона (джоба CI, ВМ). Одна строка соответствует одному файлу результатов адаптера TestIT';
COMMENT ON COLUMN test_result.id IS 'Суррогатный ключ результата';
COMMENT ON COLUMN test_result.run_pk IS 'Ссылка на прогон (run.id)';
COMMENT ON COLUMN test_result.tms_run_id IS 'Идентификатор тестового запуска в TestIT; в режиме адаптера 2 свой у каждой JVM; пусто, если адаптер не работал с TestIT';
COMMENT ON COLUMN test_result.adapter_version IS 'Версия адаптера TestIT в JVM, выполнявшей тест; unknown, если определить не удалось';
COMMENT ON COLUMN test_result.host IS 'Имя машины (ВМ или АРМ), на которой выполнялся тест';
COMMENT ON COLUMN test_result.ci_job_id IS 'Идентификатор джобы CI (CI_JOB_ID); у частей одного прогона разный, при перезапуске части новый';
COMMENT ON COLUMN test_result.ci_job_url IS 'Ссылка на джобу CI (CI_JOB_URL)';
COMMENT ON COLUMN test_result.ci_job_name IS 'Имя джобы CI (CI_JOB_NAME), например regression 2/4; показывает, какой части прогона принадлежит тест';
COMMENT ON COLUMN test_result.schema_version IS 'Версия формата файла результатов, из которого загружена строка';
COMMENT ON COLUMN test_result.uuid IS 'Уникальный идентификатор результата (TestResult.uuid); вместе с run_pk не повторяется, поэтому повторная загрузка файла не создаёт дубликатов';
COMMENT ON COLUMN test_result.external_id IS 'Идентификатор автотеста в TestIT (externalId); ключ истории теста между прогонами';
COMMENT ON COLUMN test_result.work_item_ids IS 'Идентификаторы тест-кейсов TestIT, к которым привязан автотест';
COMMENT ON COLUMN test_result.namespace IS 'Пространство имён (пакет) теста';
COMMENT ON COLUMN test_result.class_name IS 'Имя класса теста; для Cucumber может быть пусто';
COMMENT ON COLUMN test_result.name IS 'Имя теста (метод или сценарий)';
COMMENT ON COLUMN test_result.title IS 'Человекочитаемое название теста';
COMMENT ON COLUMN test_result.description IS 'Описание теста';
COMMENT ON COLUMN test_result.message IS 'Свободный текст, добавленный тестом через Adapter.addMessage; не путать с failure_message';
COMMENT ON COLUMN test_result.status IS 'Статус результата: PASSED, FAILED, SKIPPED, INPROGRESS или BLOCKED';
COMMENT ON COLUMN test_result.started_at IS 'Время начала теста';
COMMENT ON COLUMN test_result.stopped_at IS 'Время окончания теста';
COMMENT ON COLUMN test_result.duration_ms IS 'Длительность теста в миллисекундах (вычисляется из started_at и stopped_at)';
COMMENT ON COLUMN test_result.labels IS 'Метки (теги) теста';
COMMENT ON COLUMN test_result.links IS 'Ссылки теста: массив объектов {type, title, url}';
COMMENT ON COLUMN test_result.parameters IS 'Параметры теста: объект «имя — значение»; значения усечены до 2000 символов';
COMMENT ON COLUMN test_result.steps IS 'Дерево шагов теста: массив объектов {name, description, status, startMs, stopMs, parameters, attachments, failure, steps}';
COMMENT ON COLUMN test_result.attachments IS 'Вложения теста: массив объектов {path}; пути локальные, на машине выполнения; содержимое файлов не хранится';
COMMENT ON COLUMN test_result.failure_kind IS 'Вид ошибки: ASSERTION (не выполнилась проверка) или ERROR (прочая ошибка); пусто, если ошибки нет';
COMMENT ON COLUMN test_result.failure_type IS 'Полное имя класса исключения';
COMMENT ON COLUMN test_result.failure_message IS 'Сообщение исключения (усечено до 20000 символов)';
COMMENT ON COLUMN test_result.failure_trace IS 'Стектрейс исключения с причинами, без служебных кадров (усечён до 20000 символов)';
COMMENT ON COLUMN test_result.failure_suppressed IS 'Подавленные исключения: массив объектов {type, message}; для агрегатной ошибки «Test failed: N step errors» здесь реальные причины';

CREATE INDEX run_project_started_at_idx ON run (project, started_at DESC);
CREATE INDEX run_ci_commit_sha_idx ON run (ci_commit_sha);
CREATE INDEX run_ci_merge_request_iid_idx ON run (ci_merge_request_iid);
CREATE INDEX test_result_external_id_started_at_idx ON test_result (external_id, started_at DESC);
CREATE INDEX test_result_run_status_idx ON test_result (run_pk, status);
CREATE INDEX test_result_run_job_idx ON test_result (run_pk, ci_job_id);
