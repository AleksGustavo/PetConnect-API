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
├── location/          /
└── sharing/           página pública do QR com consentimento (épico #102 do app)
```

## Página pública do QR com consentimento

Fase 1 do épico [TecnoDreamer/PetConnect#102](https://github.com/TecnoDreamer/PetConnect/issues/102)
(issue #35 do app). O tutor escolhe campo a campo o que a página do QR mostra e
aceita um texto de consentimento versionado (`PublicSharingService.CONSENT_VERSION`).

| Rota | Quem | O que faz |
|---|---|---|
| `GET/PUT /api/v1/legacy-pets/{firestoreId}/public-sharing` | tutor (token) | Configuração de um pet do app em modo Firestore |
| `GET/PUT /api/v1/pets/{id}/public-sharing` | tutor (token) | Configuração de um pet da API |
| `GET /api/v1/public/pets/{publicId}` | público | Só os campos autorizados; sem consentimento, `{"shared": false}`. `Cache-Control: no-store` |
| `POST /api/v1/public/pets/{publicId}/sightings` | público | Relato anônimo; responde `201` sem corpo |

- **Campos publicáveis** (`PublicField`): `PET_NAME`, `SPECIES`, `BREED`, `COLOR`,
  `PROFILE_PHOTO`, `TUTOR_NAME`, `CONTACT_PHONE` (E.164, ver `ContactPhone`).
- **Coleções:**
  - `pet_public_sharing`: uma configuração por pet, fora de `pets`, para a
    migração não apagar o consentimento;
  - `public_sharing_consent_events`: histórico só de inclusão, com o escopo e
    sem os valores.
- **Pets do Firestore** (`FirestorePets`): a API lê `Pets/{id}` a cada acesso
  com o Admin SDK. A posse vem de lá, pets que a migração ainda não levou ao
  MongoDB funcionam, e pet excluído ou no Memorial some da página na hora.
- **Credencial:** a conta de serviço de `FIREBASE_SERVICE_ACCOUNT` precisa de
  leitura no Firestore (papel `roles/datastore.viewer` ou superior). Sem isso:
  - salvar a configuração responde `503`;
  - a página de um pet publicado responde `503`;
  - a página de um pet sem publicação continua com o mínimo neutro.
- **Exclusão:** excluir o pet ou a conta apaga a configuração, com o telefone
  e o nome de exibição.

## Acessos e avistamentos do QR

Fase 3 do épico (issue #37 do app). Dois eventos que nunca se confundem:

| Evento | Rota | O que grava |
|---|---|---|
| `qr_page_opened` | `POST /api/v1/public/pets/{publicId}/events` com `{"type":"qr_page_opened"}` (público, `202` sem corpo) | Só o pet e a hora, em `qr_access_events`. Sem IP, user-agent ou coordenada |
| `sighting_location_shared` | `POST /api/v1/public/pets/{publicId}/sightings` (público, `201` sem corpo) | Coordenada com `accuracyMeters` e `positionSource`, ou só o texto, em `locations` |

O tutor lê os dois, separados, em `GET /api/v1/legacy-pets/{firestoreId}/qr-activity`
ou `GET /api/v1/pets/{id}/qr-activity` (token; outro tutor recebe `404`).

- **Relato:**
  - precisa de uma coordenada ou de um texto (senão `400`);
  - latitude e longitude vão juntas, e a precisão fica entre 0 e 100 km;
  - `positionSource` é decidido pelo servidor: `BROWSER_GEOLOCATION` quando há
    coordenada, `NONE` quando não há. Nenhum dos dois é verificado;
  - `clientRequestId` (gerado pela página) evita relato duplicado;
  - no máximo 20 relatos por pet por hora (`429`), além do limite por IP.
- **Acessos:**
  - no máximo 30 gravados por pet por hora; acima disso a página segue
    normal e o acesso não é gravado;
  - apagados sozinhos 90 dias depois (índice TTL em `expiresAt`) e ao excluir
    o pet pela API.
- **Avistamentos:** não são apagados automaticamente. A política de retenção
  depende de revisão jurídica (issue #28 do app).

## Avisos ao tutor (push)

Fase 4 do épico (issue #36 do app). Firebase Cloud Messaging pelo Admin SDK,
com a mesma credencial de `FIREBASE_SERVICE_ACCOUNT`.

| Rota | Quem | O que faz |
|---|---|---|
| `PUT /api/v1/me/push-devices` com `{"token", "platform"}` | tutor (token) | Registra o aparelho. O token vai no corpo, nunca na URL |
| `DELETE /api/v1/me/push-devices` com `{"token"}` | tutor (token) | Remove o aparelho (ao sair da conta). Só apaga o token do próprio usuário |
| `GET/PUT /api/v1/legacy-pets/{firestoreId}/notification-preferences` | tutor (token) | `notifyOnAccess` e `notifyOnSighting` de um pet do Firestore |
| `GET/PUT /api/v1/pets/{id}/notification-preferences` | tutor (token) | O mesmo para um pet da API |

- **Quando avisa:**
  - **acesso** (`qr_page_opened`): "O QR Code de [pet] foi acessado", a
    cada abertura da página, até o limite de 30 acessos gravados por pet
    por hora. O texto diz que isso não confirma que o pet foi visto;
  - **avistamento**: "Novo avistamento de [pet]", a cada relato novo (o
    mesmo `clientRequestId` não avisa de novo).
- **Sem bloquear a página:** o envio roda numa thread virtual, fora da
  requisição pública. Não há garantia de entrega nem de tempo.
- **Payload:** só o nome do pet e os ids para o app abrir a tela certa
  (`type`, `firestorePetId`, `apiPetId`). Nada do relato, do contato de quem
  relatou ou da coordenada. No Android, canal `qr_avisos` e visibilidade
  privada na tela bloqueada.
- **Falhas:** uma nova tentativa em falha temporária; token inválido é
  apagado. Cada aviso fica em `tutor_notifications` com `PENDING`, `SENT`,
  `FAILED` ou `NO_DEVICE`, sem token nem texto, por 30 dias (TTL).
- **Coleções novas:** `push_devices` (token único por aparelho),
  `pet_notification_preferences` (uma por pet; sem documento, os dois avisos
  ficam ligados) e `tutor_notifications`.
- **Exclusão:** excluir a conta apaga aparelhos, preferências e histórico de
  avisos; excluir o pet apaga as preferências dele.
- **Credencial:** a conta de serviço precisa poder enviar mensagens pelo FCM.
  A chave `firebase-adminsdk` do projeto já pode; uma conta dedicada precisa do
  papel **Firebase Cloud Messaging API Admin** (`roles/firebasecloudmessaging.admin`),
  e a API "Firebase Cloud Messaging API (V1)" precisa estar ativada no
  projeto do Google Cloud.

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
