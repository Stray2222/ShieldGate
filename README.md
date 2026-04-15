Отличная защита сайта, сервер который может обрабатывать с 30 000 до 60 000 параллельных RTC-соединений. В режиме проксирование p.s. Не нужно внедрять в свой код, достаточно этот "бокс" разместить рядом с своим сервером и он будет работать.

🛡️ ShieldGate: High-Performance Reactive API Gateway & WAF
ShieldGate — это сверхбыстрый, легковесный защитный шлюз и Web Application Firewall (WAF), построенный на асинхронном стеке Java 21 + Netty. Спроектирован для высоконагруженных систем, где важна минимальная задержка (latency) и максимальная устойчивость к атакам.

🚀 Ключевые особенности (Key Features)
Reactive Core: Построен на базе Netty, что позволяет обрабатывать десятки тысяч одновременных соединений при минимальном потреблении ресурсов процессора и памяти.
Streaming JSON WAF: Уникальная система инспекции трафика через Jackson Streaming API. Защищает от "JSON-бомб" и XSS/SQL-инъекций на лету, не загружая всё тело запроса в память (Zero-copy подход).
Dynamic Rate Limiting: Интегрированный алгоритм Token Bucket (Bucket4j) для защиты от DDoS-атак и злоупотреблений API.
Smart Reputation System: Автоматическая система банов. Если клиент систематически нарушает правила безопасности (WAF), шлюз временно блокирует его IP через высокопроизводительный кэш Caffeine.
Hot Reload: Горячая перезагрузка конфигурации через Java NIO WatchService. Изменяйте лимиты, порты и API-ключи в config.yaml без остановки сервера.
Full Observability: Интеграция с Prometheus и Grafana «из коробки». Отслеживайте RPS, количество блокировок и состояние системы в реальном времени.
Correlation ID Logging: Сквозная идентификация запросов. Каждый запрос получает уникальный ID, который пробрасывается в заголовках до бэкенда, обеспечивая идеальную прослеживаемость логов.

🛠 Технологический стек (Tech Stack)
Runtime: Java 21 (LTS)
Network: Netty (Async IO, Connection Pooling)
Security: Jackson (Streaming JSON), Bucket4j (Rate Limit), Caffeine (Reputation Cache)
Config: SnakeYAML + WatchService (Hot Reload)
Observability: Micrometer, Prometheus, Grafana
DevOps: Docker, Docker Compose, Maven
Load Testing: k6 (Grafana k6)

📐 Архитектура (Architecture)
Traffic Shield Layer: Фильтрация по IP White-list и проверка API-ключей.
Rate Limiter: Проверка квот на основе алгоритма Token Bucket.
WAF Engine: Потоковый анализ JSON на наличие сигнатур атак.
Reverse Proxy: Пересылка очищенного трафика на бэкенд через оптимизированный пул соединений.
Metrics Exporter: Сбор статистики для Prometheus.
