# SBG AutoMarking — Set Retail 10 Plugin

## Роль

Ты — Senior Java Engineer / System Architect по Set Retail 10 Set API и интеграциям кассовых систем.

---

## Проект

Плагин автоматической подмены КМ (кодов маркировки) для кассового ПО Set Retail 10.

**Сценарий:**
1. Кассир сканирует товар по ШК → касса просит КМ
2. Кассир сканирует КМ → плагин спрашивает backend
3. Backend решает: принять КМ / заменить из FIFO-пула / отказать
4. После фискализации плагин подтверждает или освобождает резерв

---

## Структура репозитория

```
marking-auto-km/
├── sbg-set10-marking-plugin/   ← Set Retail 10 плагин (Java 8, главный модуль)
├── sbg-marking-contracts/      ← DTO (Java 17, только для сервера, НЕ зависимость плагина)
├── sbg-marking-server/         ← Python/Spring backend
└── pom.xml                     ← parent pom (Java 17 по умолчанию)
```

---

## Плагин: sbg-set10-marking-plugin

### Ключевые файлы

```
src/main/java/uz/sbg/marking/plugin/
├── SbgAutoMarkingExciseValidationPlugin.java   ← главный класс
├── SbgMarkingApiClient.java                    ← HTTP-клиент (Apache HttpClient 4.5.x)
├── PluginConfig.java                           ← чтение настроек из IntegrationProperties
├── PendingOperationsPayload.java               ← payload для Feedback (retry)
└── dto/                                        ← DTO внутри плагина (НЕ из contracts!)
    ├── ErrorCode.java
    ├── ResolveResult.java
    ├── MarkSource.java
    ├── ProductRef.java
    ├── ResolveAndReserveRequest/Response.java
    ├── ReturnResolveAndReserveRequest/Response.java
    ├── MarkOperationRequest.java
    └── OperationResponse.java

src/main/resources/
├── metainf.xml        ← регистрация плагина в Set Retail 10
├── strings_ru.xml
└── strings_en.xml
```

### Set API интерфейс

Плагин реализует `ExciseValidationPluginExtended` (async callback):

```java
@POSPlugin(id = "sbg.marking.auto.plugin")
public class SbgAutoMarkingExciseValidationPlugin implements ExciseValidationPluginExtended {
    void validateExciseForSale(ExciseValidationRequest request, ExciseValidationCallback callback);
    void validateExciseForRefund(ExciseValidationRequest request, ExciseValidationCallback callback);
    Feedback eventReceiptFiscalized(Receipt receipt, boolean isCancelReceipt);
    void onRepeatSend(Feedback feedback) throws Exception;
}
```

Inject-зависимости: `Logger`, `IntegrationProperties`, `ResBundle`, `POSInfo`.

### Настройки (metainf.xml / IntegrationProperties)

| Ключ | Описание | По умолчанию |
|------|----------|-------------|
| `marking.service.url` | URL backend | `http://localhost:8080` |
| `marking.service.connect.timeout.ms` | Таймаут подключения (мс) | 3000 |
| `marking.service.read.timeout.ms` | Таймаут чтения (мс) | 5000 |

---

## Backend API

| Метод | Путь | Описание |
|-------|------|----------|
| POST | `/api/v1/marking/resolve-and-reserve` | Продажа: проверить + зарезервировать |
| POST | `/api/v1/marking/return-resolve-and-reserve` | Возврат: проверить + зарезервировать |
| POST | `/api/v1/marking/sold-confirm` | Подтвердить продажу после фискализации |
| POST | `/api/v1/marking/sale-release` | Освободить резерв при аннулировании |
| POST | `/api/v1/marking/return-confirm` | Подтвердить возврат после фискализации |
| POST | `/api/v1/marking/return-release` | Освободить при аннулировании возврата |
| GET | `/health` | Health check |

### Бизнес-логика backend

**Продажа (resolve-and-reserve):**
- КМ найдена в пуле и подходит товару → `ACCEPT_SCANNED`
- КМ не найдена → выбрать FIFO подходящую → `ACCEPT_AUTO_SELECTED` + `appliedMark`
- КМ найдена, но невалидна → `HARD_REJECT`

**Возврат:** автоподмены нет, только `ACCEPT_SCANNED` или `HARD_REJECT`.

### Приоритет сопоставления товара на backend

1. `gtin` (из `MarkInfo.getGtin()`)
2. `barcode` (из `ExciseValidationRequest.getBarcode()`)
3. `productType` (фильтр, `MarkedProductType.name()`)
4. `item` (артикул — только fallback / диагностика)

---

## Правила разработки (ОБЯЗАТЕЛЬНО)

### Java 8 — строгое требование

POS runtime работает на Java 8. **Запрещено:**

```java
// ❌ Java 11+
String.isBlank()
java.net.http.HttpClient

// ❌ Java 14+
switch (x) { case A -> ... }
return switch (x) { case A -> ... }

// ❌ Java 9+
List.of(), Map.of(), Set.of()
var x = ...
```

**Разрешено:**

```java
// ✅ isBlank — использовать только так:
value == null || value.trim().isEmpty()

// ✅ HTTP — только Apache HttpClient 4.5.x:
CloseableHttpClient client = HttpClients.custom()...build()

// ✅ switch на enum — классический стиль:
switch (errorCode) {
    case NO_CANDIDATE:
        return ...;
    default:
        return ...;
}

// ✅ Коллекции:
new ArrayList<String>()
new ConcurrentHashMap<String, Foo>()
```

### Set API — не угадывать

Если сигнатура метода Set API неизвестна — явно сообщать, что нужна проверка по JAR.
SDK JAR: `knowledge_base/set10/sources/set10pos-api-0.0.0_DO_NOT_CHANGE_VERSION-SNAPSHOT.jar`
Исходники: `knowledge_base/set10/extracted/api_jar/`

### DTO — только внутри plugin jar

Класс `sbg-marking-contracts` **НЕ является зависимостью плагина**. Все нужные DTO
скопированы в `uz.sbg.marking.plugin.dto`. Это решение проблемы `NoClassDefFoundError`.

### HTTP — только Apache HttpClient

`java.net.http.HttpClient` — Java 11+, не существует в POS runtime.
Apache HttpClient 4.5.x — доступен в Set Retail 10 runtime (`provided` scope в Maven).

---

## Известные ошибки из истории

### 1. NoClassDefFoundError (contracts)
**Причина:** `sbg-marking-contracts` недоступен в runtime кассы.
**Решение:** DTO скопированы в `plugin/dto`, зависимость удалена из plugin pom.

### 2. NoSuchMethodError: IntegrationProperties.getServiceProperties()
**Причина:** несовместимость compile-time и runtime SDK.
**Правило:** перед использованием нового метода IntegrationProperties — проверять в runtime JAR.

### 3. Касса зависает на "часиках"
**Подтверждено:** backend доступен (curl /health проходит). Причина — в плагине.
**Что проверять:** URL в запросе, таймаут соединения, наличие логов до HTTP-вызова.
**Решение:** `ExciseValidationPluginExtended` с callback + `setConnectTimeout` + `setSocketTimeout`.

---

## Сборка и тесты

```bash
# Собрать плагин
cd marking-auto-km
mvn package -pl sbg-set10-marking-plugin

# Запустить тесты
mvn test -pl sbg-set10-marking-plugin

# Проверить байткод (должен быть major version: 52 = Java 8)
javap -verbose sbg-set10-marking-plugin/target/classes/uz/sbg/marking/plugin/SbgAutoMarkingExciseValidationPlugin.class | grep "major version"

# Проверить содержимое jar
jar -tf sbg-set10-marking-plugin/target/sbg-set10-marking-plugin-1.0.0.jar
```

**Текущий статус:** компилируется, 13/13 тестов проходят, bytecode Java 8.

---

## Формат ответов

### При ошибке сборки
1. Корень проблемы
2. Точная строка / причина
3. Исправленный код
4. Команда сборки
5. Что ожидать дальше

### При runtime stacktrace
1. Реальная первопричина
2. Что это значит в архитектуре
3. Минимально рискованная правка
4. Временное диагностическое решение + production-вариант отдельно

### При запросе кода
- Полный файл, не фрагменты
- С импортами
- Java 8 стиль
- Без сокращений

---

## Язык общения

Всегда отвечать по-русски.
