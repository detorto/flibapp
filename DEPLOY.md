# Деплой бекенда на Fly.io

## 1. Подготовка (один раз)

### Анонимный email
Зарегистрируй почту на https://proton.me (бесплатно, без телефона).
Запиши пароль в менеджер паролей.

### Fly.io аккаунт
1. Зайди на https://fly.io/app/sign-up
2. Зарегистрируйся через анонимный email
3. Привяжи карту (Fly не списывает за free tier; можно виртуальную)

### Установи fly CLI
```bash
brew install flyctl
```

## 2. Деплой

```bash
cd backend

# Логин (откроется браузер)
fly auth login

# Первый деплой (создаст приложение)
fly launch --copy-config --yes

# Последующие деплои
fly deploy
```

## 3. Проверка

```bash
# Статус
fly status

# Логи
fly logs

# Тест
curl https://flibusta-proxy.fly.dev/health
```

Адрес бекенда будет: `https://flibusta-proxy.fly.dev`
Его нужно ввести в настройках приложения.

## 4. Если заблокируют

```bash
# Сменить имя приложения
fly apps rename flibusta-proxy new-name-here

# Или пересоздать
fly apps destroy flibusta-proxy
# Поменять app в fly.toml
fly launch --copy-config --yes
```

## Секреты

НЕ коммить в git:
- Fly API token (`fly auth token` — показывает текущий)
- Любые пароли/ключи

Если нужен Tor-прокси:
```bash
fly secrets set TOR_PROXY=127.0.0.1:9050
```
