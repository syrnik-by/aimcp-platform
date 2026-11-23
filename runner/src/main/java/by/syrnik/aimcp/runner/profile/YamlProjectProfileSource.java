package by.syrnik.aimcp.runner.profile;

import by.syrnik.aimcp.core.profile.InvalidProfileException;
import by.syrnik.aimcp.core.profile.ProjectProfile;
import by.syrnik.aimcp.core.profile.ProjectProfileSource;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Профиль проекта из файла {@code at-profile.yaml} (контракт docs/project-profile.md). Путь к файлу передаёт
 * вызывающий, из настройки приложения.
 * <p>
 * Файл из репозитория — внешние данные: {@link SafeConstructor} (теги {@code !!...} не создают произвольные классы),
 * дубликаты ключей запрещены, размер ограничен. Исключения SnakeYAML содержат фрагмент файла, поэтому в сообщение
 * попадает только номер строки, а сама ошибка не передаётся как причина.
 */
public class YamlProjectProfileSource implements ProjectProfileSource {

    static final int MAX_SIZE_BYTES = 64 * 1024;

    private final Path file;

    public YamlProjectProfileSource(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    @Override
    public ProjectProfile load() {
        return parse(readText());
    }

    /** Разбирает текст профиля. Публичен, чтобы проверять разбор без файла. */
    public static ProjectProfile parse(String text) {
        return new ProfileParser().parse(readTree(text));
    }

    private String readText() {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] bytes = in.readNBytes(MAX_SIZE_BYTES + 1);
            if (bytes.length > MAX_SIZE_BYTES) {
                throw new InvalidProfileException("файл профиля больше 64 КБ");
            }
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new InvalidProfileException("файл профиля не в кодировке UTF-8");
        } catch (IOException e) {
            throw new InvalidProfileException("файл профиля не прочитан: " + file, e);
        }
    }

    private static Object readTree(String text) {
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            return new Yaml(new SafeConstructor(), new Representer(), new DumperOptions(), options).load(text);
        } catch (MarkedYAMLException e) {
            Mark mark = e.getProblemMark();
            throw new InvalidProfileException("YAML не разобран" + (mark == null ? ""
                    : ": строка " + (mark.getLine() + 1) + ", столбец " + (mark.getColumn() + 1)));
        } catch (YAMLException e) {
            throw new InvalidProfileException("YAML не разобран");
        } catch (StackOverflowError e) {
            // PORTING: SnakeYAML 1.29 не ограничивает глубину вложенности; в новых версиях есть лимит в LoaderOptions
            throw new InvalidProfileException("YAML не разобран: слишком глубокая вложенность");
        }
    }
}
