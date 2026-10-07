# RayAuction

Аукцион с фиксированной ценой («купить сейчас») для **Paper 1.21.11** и **Folia**. Java 21.

Игрок выставляет предмет по своей цене, покупатель жмёт «купить» — без ставок, без ожидания. Всё остальное — лимиты, комиссия, почта, история, мульти-валюты — настраивается.

## Возможности

- **Buy-now лоты** с фиксированной ценой и выбором срока из `default-durations`.
- **Folia из коробки**: планирование через Region/Global/Async Scheduler (Folia-совместимый `platform/` слой, определение типа сервера в рантайме).
- **Мультивалютность**: опыт игрока, Vault, CoinsEngine, PlayerPoints — одновременно, с выбором валюты в меню продажи.
- **Комиссия (tax)**: процент с продажи, сжигается или уходит на серверный аккаунт.
- **Почта (mailbox)**: возврат истёкших/отменённых лотов и доходов офлайн-продавцам; выдача при входе.
- **История операций** с фильтрами (продажи/покупки/истёкшие/отменённые/возвраты).
- **Поиск и фильтры**: текстовый поиск с дебаунсом, категории, сортировка (дата/цена).
- **Просмотр содержимого** шалкеров и бочек перед покупкой (27/54-слотовое превью).
- **Лимиты лотов**: по умолчанию + через права `rayauction.limit.<N>`, изменение админ-командой.
- **Чёрный список** предметов: материалы, подстроки в имени/лоре, NBT-ключи, шалкеры целиком.
- **Анти-дюп**: все операции денег/предметов в одной SQL-транзакции, оптимистичная блокировка по `version`, `FOR UPDATE` на чтение лота, дебаунс действий игрока, снятие лота до выдачи.
- **Сохранение CustomModelData** и прочего item-компонента при сериализации.
- **PlaceholderAPI**: `%rayauction_active_auctions%` и другие (см. ниже).
- **Discord-вебхуки** о событиях (опционально).
- **Звуковая обратная связь** — настраиваемые звуки покупки, продажи, снятия, забора и ошибок (секция `sounds:`).
- **Массовое снятие лотов** — кнопка «Снять все» в меню «Мои лоты», предметы вернутся в инвентарь или почту.
- **Синхронизация между серверами** через таблицу событий в БД (polling, Redis не нужен).

## Установка

1. Положите `rayauction-<version>.jar` в `plugins/`.
2. Запустите сервер — создадутся `plugins/RayAuction/config.yml`, `messages.yml`, `gui.yml`, `blacklist.yml`.
3. Настройте базу данных и валюты в `config.yml`, выполните `/ahadmin reload`.

Требуется **Java 21**. Paper 1.21.11+ или Folia. Плагины-экономики (Vault, CoinsEngine, PlayerPoints) — опциональны и подключаются автоматически при наличии.

Slim-сборка (`-slim`) не содержит библиотек внутри: при первом запуске встроенный `PluginLoader` скачает H2, HikariCP, Caffeine и PostgreSQL-драйвер с зеркала Maven Central (нужен интернет на сервере, один раз — затем кэшируется). Если сервер без интернета — либо положите jar'ы этих библиотек в `plugins/RayAuction/libs/` (загружаются оттуда автоматически), либо используйте полную сборку `rayauction-<version>-full.jar` со всеми библиотеками внутри.

## Команды

Основная — `/ah` (алиасы: `/ahouse`, `/auction`):

| Команда | Право | Описание |
|---|---|---|
| `/ah` | `rayauction.use` | открыть браузер аукциона |
| `/ah sell` | `rayauction.sell` | выставить предмет из основной руки |
| `/ah search <текст>` | `rayauction.search` | поиск по названию |
| `/ah view <игрок>` | `rayauction.view` | лоты конкретного игрока |
| `/ah history` | `rayauction.history` | своя история операций |
| `/ah mailbox` | `rayauction.use` | забрать возвраты и доходы |

Административная — `/ahadmin` (алиас: `/rayauctionadmin`), право `rayauction.admin`:

| Команда | Описание |
|---|---|
| `/ahadmin reload` | перечитать конфиги |
| `/ahadmin history <игрок>` | история игрока |
| `/ahadmin limit set <игрок> <N>` | задать персональный лимит |
| `/ahadmin limit give <игрок> <N>` | увеличить лимит |
| `/ahadmin limit take <игрок> <N>` | уменьшить лимит |
| `/ahadmin limit reset <игрок>` | сбросить на значение из конфига/прав |
| `/ahadmin remove` | снять свой лот (консоль: по id) |
| `/ahadmin give <игрок> <кол-во>` | начислить опыт-валюту игроку |
| `/ahadmin currency <id>` | информация о валюте |
| `/ahadmin stats` | статистика аукциона: активные лоты, топы, самый дешёвый лот |

Права-лимиты: `rayauction.limit.<N>` — максимум активных лотов; `rayauction.limit.unlimited` — без лимита. Обход чёрного списка: `rayauction.blacklist.bypass`.

## Конфигурация

| Файл | Назначение |
|---|---|
| `config.yml` | база данных, валюты, комиссия, лимиты, сроки, синхронизация, Discord |
| `messages.yml` | все тексты (MiniMessage); у GUI-сообщений нет префикса (`unprefixed-keys`) |
| `gui.yml` | слоты кнопок и предметов всех меню; кнопки ищутся по уникальному id, меню — по `menu` |
| `blacklist.yml` | материалы, подстроки, NBT-ключи, `block-shulker-boxes`, `bypass-permission` |

При обновлении версии конфига плагин сам делает бэкап `config.yml.v<старая>.backup` и поднимает `config-version`. Конфиг из более новой версии, чем сборка, — отказ с понятной ошибкой.

### Звуки

Секция `sounds:` в `config.yml` подключает звук к событию: `buy` (покупка), `sell` (выставление), `cancel` (снятие, включая «Снять все»), `claim` (забор из почты), `error` (любая неудача). Пустое `name` — звук выключен. Ключ звука — любой ванильный идентификатор (`entity.player.levelup`), `volume: 0.0-10.0`, `pitch: 0.5-2.0`.

### Базы данных

По умолчанию — встраиваемая **H2** (файл в `plugins/RayAuction/data/`). Для сети серверов — **MySQL** или **PostgreSQL**:

```yaml
database:
  type: mysql            # h2 | mysql | postgresql
  host: "127.0.0.1"
  port: 3306
  name: "rayauction"
  user: "rayauction"
  password: "secret"
  pool-size: 10
```

Схема создаётся автоматически (миграции V1–V3 с историей версий и контрольными суммами; правка применённых миграций пресекается). Цены — `DECIMAL(20,4)`, что вмещает scale-4 валюты CoinsEngine.

### Несколько серверов на одну базу

```yaml
multi-server:
  enabled: true
  server-id: "survival-1"   # уникально для каждого сервера!
  poll-interval-seconds: 2
```

Каждый сервер опрашивает таблицу `rayauction_sync_events` и подхватывает чужие покупки/возвраты. События со своим `server-id` игнорируются.

## PlaceholderAPI

Идентификатор `rayauction`:

| Плейсхолдер | Значение |
|---|---|
| `%rayauction_active_auctions%` | активных лотов на сервере |
| `%rayauction_top_seller_name%` / `_amount%` | лучший продавец |
| `%rayauction_top_buyer_name%` / `_amount%` | лучший покупатель |
| `%rayauction_cheapest_item%` / `_price%` | самый дешёвый активный лот |
| `%rayauction_player_sales%` | активных лотов у игрока |
| `%rayauction_player_sold%` / `%rayauction_player_bought%` | статистика игрока |
| `%rayauction_player_limit%` / `_remaining%` | лимит и остаток |
| `%rayauction_player_earned%` / `%rayauction_player_spent%` | заработано / потрачено |

## Сборка

```bash
./gradlew build         # slim-сборка (основная)
./gradlew shadowJar     # полная сборка для серверов без интернета
```

Артефакты: `build/libs/rayauction-<version>.jar` — slim (только классы и ресурсы плагина, ~0.4 МБ; библиотеки подтягиваются `RayAuctionLibraries` при загрузке) и `build/libs/rayauction-<version>-full.jar` (~5 МБ; H2, PostgreSQL, HikariCP, Caffeine затенены с relocate в `ray.labs.rayauction.libs.*`). Остальное (Adventure, Gson, драйвер MySQL) берётся из самого Paper. Версии библиотек в `RayAuctionLibraries` и `build.gradle.kts` должны совпадать. Проверки: `./gradlew test`, форматирование — `./gradlew spotlessCheck` (входит в `build`).

## Архитектура

```
ray.labs.rayauction
├── command/        Brigadier-команды (Paper LifecycleEvents)
├── listener/       инвентарь, чат-поиск, вход игрока
├── domain/         чистая логика: аукцион, деньги, лимиты, blacklist — без Bukkit
│   └── port/       интерфейсы портов (репозитории, экономика, предметы)
├── storage/        JDBC (H2/MySQL/PostgreSQL), миграции, кэш Caffeine
├── sync/           брокер событий на опросе БД (без Redis)
├── economy/        мосты к Vault/CoinsEngine/PlayerPoints (рефлексия) + опыт
├── gui/            меню на InventoryHolder: браузер, продажа, подтверждение, история, почта, превью
├── config/         типизированные записи + YamlNode, messages.yml (MiniMessage)
├── discord/        вебхуки
├── placeholder/    PlaceholderAPI expansion
├── platform/       планировщик Paper/Folia, сериализация предметов
└── util/           ItemSerializer, Debouncer и пр.
```

Инварианты: `domain/` не импортирует Bukkit; состояние игроков — только `Map<UUID, …>`; весь I/O — асинхронно; GUI определяется `InventoryHolder`, не заголовком; клики отменяются до обработки; SQL — только через `PreparedStatement`.

## Лицензия

© AkyRayy. Все права защищены.
