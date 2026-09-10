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

Pré-requisitos: Docker. (JDK 21 + Maven só para rodar os testes fora do container.)

### Tudo em container (recomendado nesta máquina)

O Windows desta máquina bloqueia as conexões de loopback que o Tomcat (NIO)
precisa para iniciar (`Selector.open` → *Unable to establish loopback connection*).
Rodar a API num container Linux resolve.

```bash
cp .env.example .env        # e ajuste FIREBASE_SA_PATH para o caminho do seu JSON
docker compose up -d --build
docker compose logs -f api
```

| Recurso | URL |
|---|---|
| Ping | http://localhost:8080/api/v1/ping |
| Health | http://localhost:8080/actuator/health |
| Swagger | http://localhost:8080/swagger-ui.html |
| MongoDB | `mongodb://localhost:27018/petconnect` (container `petconnect-mongo`) |

### Só o banco + API pela IDE

Se a sua máquina não tiver o problema de loopback:

```bash
docker compose up -d mongo
mvn spring-boot:run -Dspring-boot.run.profiles=dev,local   # usa src/main/resources/application-local.yml
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

## Estado atual (FASE 2)

- **FASE 1** — esqueleto: sobe, health/ping/swagger, segurança stateless, formato de erro único.
- **FASE 2** — autenticação:
  - `FirebaseTokenAuthenticationFilter` valida `Authorization: Bearer <Firebase ID Token>`
    via Admin SDK e popula o `SecurityContext`. Sem a credencial configurada, rotas
    protegidas respondem `401` (dev/CI seguem funcionando).
  - Módulo `user`: documento `users` (`firebaseUid` único, `roles`, timestamps),
    provisionamento no 1º acesso, `GET /api/v1/me` e `PATCH /api/v1/me`.
  - 7 testes verdes (context load + `MeControllerTest` com `FirebaseTokenVerifier` mockado).

### Configurar a credencial do Firebase (dev)

1. Firebase Console → Configurações do projeto → Contas de serviço → **Gerar nova chave privada**
   (uma chave **dedicada ao backend**, separada da usada no script de backup).
2. Salve o JSON **fora do repositório**.
3. Aponte a env var antes de rodar:
   ```bash
   export FIREBASE_SERVICE_ACCOUNT="/caminho/para/petconnect-backend-sa.json"   # bash
   $env:FIREBASE_SERVICE_ACCOUNT = "C:\caminho\para\petconnect-backend-sa.json" # PowerShell
   ```
   (aceita também o JSON inteiro em base64, útil para hosts que só têm env vars)

Próximo: **FASE 3** — migração de dados (`users` + `pets` + `locations`) do export do Firestore.
