# PetConnect API

Backend REST do **PetConnect** — Spring Boot + MongoDB + Firebase Authentication.

> Repositório separado do app Flutter ([`../PetConnect`](../PetConnect)).
> O app **nunca** acessa o MongoDB direto — só esta API, por HTTPS + JSON.
> Firebase responde "quem é o usuário?"; esta API responde "o que ele pode fazer?".

Plano e contexto da migração: `../PetConnect/docs/migration/firestore-to-spring-mongodb.md`.

## Stack

| | |
|---|---|
| Java | 21 |
| Spring Boot | 3.4.2 (Web, Security, Validation, Data MongoDB, Actuator) |
| Docs | springdoc-openapi (Swagger UI) |
| Auth | Firebase Admin SDK — valida o ID Token (a partir da FASE 2) |
| Banco | MongoDB (local no dev; Atlas M0 no staging/prod) |
| Testes | JUnit 5, Spring Test, MockMvc, MongoDB embarcado (flapdoodle) |
| Build | Maven |

## Arquitetura — monólito modular

```
com.petconnect.api
├── PetConnectApiApplication
├── shared/            config (security, CORS, OpenAPI) · error (formato único) · web (constantes)
├── user/              \
├── pet/                \
├── vaccine/             |  1 módulo por contexto. Cada um:
├── appointment/         |  domain · application · infrastructure · web
├── medicalrecord/      /   (vazios na FASE 1)
└── location/          /
```

## Rodar em desenvolvimento

Pré-requisitos: JDK 21, Maven, e um MongoDB local (`mongodb://localhost:27017`) — ou use Docker:

```bash
docker run -d --name petconnect-mongo -p 27017:27017 mongo:7
```

```bash
mvn spring-boot:run
```

| Recurso | URL |
|---|---|
| Ping público | http://localhost:8080/api/v1/ping |
| Health | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |

## Ambientes

`APP_ENV` seleciona o perfil (`dev` padrão, `staging`, `prod`). Variáveis:

| Var | Uso | Obrigatória |
|---|---|---|
| `APP_ENV` | perfil ativo | não (default `dev`) |
| `PORT` | porta HTTP | não (default 8080) |
| `MONGODB_URI` | conexão Mongo | sim em staging/prod |
| `CORS_ALLOWED_ORIGINS` | origens liberadas (lista separada por vírgula) | sim em staging/prod |
| `FIREBASE_SERVICE_ACCOUNT` | JSON da conta de serviço (caminho ou base64) | sim a partir da FASE 2 |
| `FIREBASE_PROJECT_ID` | projeto Firebase | não (default `pet-connect-c53f1`) |

**Nenhum segredo é versionado.** Chaves ficam em env var / secret manager do host.

## Formato de erro

Toda resposta de erro:

```json
{ "timestamp": "2026-09-10T12:34:56.789Z", "status": 404, "code": "RESOURCE_NOT_FOUND", "message": "..." }
```

Sem stack trace, sem detalhe interno.

## Estado atual (FASE 1)

Esqueleto: sobe, health/ping/swagger funcionam, segurança stateless com rotas
públicas liberadas e o resto exigindo autenticação (mecanismo entra na FASE 2).
Nenhum módulo de domínio implementado ainda.
