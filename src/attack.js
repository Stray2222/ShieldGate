import http from 'k6/http';

export let options = {
    stages: [
        { duration: '20s', target: 500 },  // Быстрый разгон
        { duration: '1m', target: 2000 }, // Пик нагрузки (2000 пользователей)
        { duration: '10s', target: 0 },   // Спад
    ],
};

export default function () {
    const url = 'http://localhost:8080/';

    // Генерируем "жирный" JSON для нагрузки на JsonInspector
    const heavyJson = JSON.stringify({
        payload: Array(100).fill({ // Увеличил до 100 элементов
            info: "Stress test data",
            data: "check: select * from users; <script>alert(1)</script>", // Проверка Regex
            nested: { a: { b: { c: { d: "deep-value" } } } }
        })
    });

    // ДОБАВИЛ: Обязательные заголовки для прохождения авторизации
    const params = {
        headers: {
            'Content-Type': 'application/json',
            'X-API-KEY': 'super-secret-token-123'
        },
        timeout: '20s' // Запас по времени на обработку тяжелых запросов
    };

    // Отправляем POST запрос
    http.post(url, heavyJson, params);
}
