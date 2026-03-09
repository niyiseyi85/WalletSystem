# Wallet System — 3Line Backend Assessment

A simple Wallet System built with Spring Boot that supports user creation with auto-provisioned accounts and intra-system fund transfers.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.5.0 |
| Persistence | Spring Data JPA + Hibernate |
| Database | H2 (in-memory, auto-reset on restart) |
| Validation | Spring Boot Validation (Jakarta Bean Validation) |
| Build Tool | Maven (Maven Wrapper included) |
| Utilities | Lombok |

---

## Getting Started

### Prerequisites
- Java 17 or higher installed
- No additional setup required — H2 runs in-memory

### Run the Application

```bash
cd test
./mvnw spring-boot:run
```

On Windows:

```bash
cd test
mvnw.cmd spring-boot:run
```

The application starts on **http://localhost:9090**

### Run Tests

```bash
./mvnw test
```

### Access H2 Console (Database Browser)

Navigate to: **http://localhost:9090/h2-console**

| Field | Value |
|---|---|
| JDBC URL | `jdbc:h2:mem:testdb` |
| Username | `sa` |
| Password | _(leave blank)_ |

---

## API Reference

### Base URL
```
http://localhost:9090/api/v1/wallet
```

All responses follow a consistent envelope:
```json
{
  "success": true,
  "message": "...",
  "data": { ... },
  "timestamp": "2026-03-09T10:00:00"
}
```

---

### 1. Create User & Account

**POST** `/api/v1/wallet/users`

Creates a new user and automatically provisions a linked account with an account number and a zero wallet balance.

**Request Body:**
```json
{
  "email": "john.doe@example.com"
}
```

**Response — 201 Created:**
```json
{
  "success": true,
  "message": "User and account created successfully",
  "data": {
    "userId": 1,
    "email": "john.doe@example.com",
    "accountNumber": "3L-A1B2C3D4E5",
    "balance": 0
  },
  "timestamp": "2026-03-09T10:00:00"
}
```

| Status | Scenario |
|---|---|
| `400` | Invalid or missing email |
| `409` | Email already registered |

---

### 2. Fund Transfer Between Accounts

**POST** `/api/v1/wallet/transfers`

Transfers funds from one account to another. The operation is fully atomic.

**Request Body:**
```json
{
  "fromAccount": "3L-A1B2C3D4E5",
  "toAccount": "3L-F6G7H8I9J0",
  "amount": 500.00
}
```

**Response — 200 OK:**
```json
{
  "success": true,
  "message": "Transfer completed successfully",
  "data": {
    "fromAccount": "3L-A1B2C3D4E5",
    "toAccount": "3L-F6G7H8I9J0",
    "amount": 500.00,
    "newFromBalance": 500.00
  },
  "timestamp": "2026-03-09T10:05:00"
}
```

| Status | Scenario |
|---|---|
| `400` | Invalid request (zero/negative amount, same account) |
| `404` | Source or destination account not found |
| `422` | Insufficient funds in source account |

---

### 3. Get Account Balance

**GET** `/api/v1/wallet/accounts/{accountNumber}/balance`

Returns the current wallet balance for an account.

**Response — 200 OK:**
```json
{
  "success": true,
  "message": "Account balance retrieved successfully",
  "data": {
    "accountNumber": "3L-A1B2C3D4E5",
    "balance": 500.00
  },
  "timestamp": "2026-03-09T10:10:00"
}
```

| Status | Scenario |
|---|---|
| `404` | Account not found |

---

### 4. Get Transaction History

**GET** `/api/v1/wallet/accounts/{accountNumber}/transactions`

Returns all debit and credit transactions for an account, ordered most recent first.

**Response — 200 OK:**
```json
{
  "success": true,
  "message": "Transaction history retrieved successfully",
  "data": [
    {
      "id": 1,
      "fromAccount": "3L-A1B2C3D4E5",
      "toAccount": "3L-F6G7H8I9J0",
      "amount": 500.00,
      "balanceAfterDebit": 500.00,
      "status": "SUCCESS",
      "createdAt": "2026-03-09T10:05:00"
    }
  ],
  "timestamp": "2026-03-09T10:10:00"
}
```

| Status | Scenario |
|---|---|
| `404` | Account not found |

---

## Project Structure

```
src/main/java/com/example/test/
├── controller/
│   └── WalletController.java          REST endpoints
├── dto/
│   ├── request/
│   │   ├── CreateUserRequest.java
│   │   └── TransferRequest.java
│   └── response/
│       ├── ApiResponse.java           Generic response envelope
│       ├── UserAccountResponse.java
│       └── TransferResponse.java
├── exception/
│   ├── AccountNotFoundException.java
│   ├── InsufficientFundsException.java
│   ├── InvalidTransferException.java
│   ├── DuplicateEmailException.java
│   └── GlobalExceptionHandler.java    Centralised error handling
├── model/
│   ├── User.java
│   ├── Account.java
│   └── WalletBalance.java
├── repository/                        Domain repository interfaces
│   ├── UserRepository.java
│   ├── AccountRepository.java
│   ├── WalletBalanceRepository.java
│   └── impl/                          JPA-backed implementations
│       ├── UserRepositoryImpl.java
│       ├── AccountRepositoryImpl.java
│       └── WalletBalanceRepositoryImpl.java
├── infrastructure/
│   └── jpa/                           Spring Data JPA interfaces
│       ├── UserJpaRepository.java
│       ├── AccountJpaRepository.java
│       └── WalletBalanceJpaRepository.java
├── service/
│   ├── WalletService.java             Service contract
│   └── impl/
│       └── WalletServiceImpl.java     Business logic implementation
└── TestApplication.java
```

---

## Design Decisions & Assumptions

### Assumptions
1. **One account per user** — each user is provisioned with exactly one account at creation time.
2. **Account number format** — `3L-` prefix followed by 10 alphanumeric characters derived from a UUID (e.g. `3L-A1B2C3D4E5`). This is unique, brand-consistent, and collision-resistant.
3. **Currency** — no multi-currency support; all balances are treated as a single currency (NGN implied).
4. **No authentication layer** — the assessment scope does not require authentication/authorisation. In production this would be secured with JWT or OAuth2.
5. **No credit/top-up endpoint** — the assessment specifies fund transfer between accounts. Topping up a wallet from an external source is outside scope.
6. **In-memory database** — H2 is used for simplicity. The application is production-ready to swap to PostgreSQL or MySQL by changing `application.properties` and the datasource driver.

### Architecture Decisions

**Repository Pattern (domain layer decoupled from JPA)**
The service layer depends exclusively on domain repository interfaces (`AccountRepository`, `UserRepository`, `WalletBalanceRepository`). The JPA implementations live in `repository/impl/` and the raw Spring Data interfaces in `infrastructure/jpa/`. This means the business logic is completely testable without a database and the persistence technology can be swapped without touching service code.

**Pessimistic Locking on WalletBalance**
`WalletBalanceJpaRepository.findByIdWithLock` uses `PESSIMISTIC_WRITE` lock mode. This ensures that when two concurrent transfers read the same source balance, one will block and wait — preventing the classic double-spend race condition (overdraft on concurrent requests).

**`@Version` on WalletBalance**
An optimistic lock version field is included on `WalletBalance` as a secondary safeguard. If pessimistic locking is ever removed or bypassed, Hibernate will throw an `OptimisticLockException` on a stale write, preventing silent data corruption.

**`@Transactional` with explicit isolation**
`createUserAndAccount` uses the default `REQUIRED` transaction. `doTransfer` uses `READ_COMMITTED` isolation explicitly — it only reads committed data, which is the correct baseline for financial operations combined with the pessimistic lock.

**Cascading**
`User` → `Account` → `WalletBalance` uses `CascadeType.ALL` with `orphanRemoval = true`. This means:
- Saving a `User` automatically creates the linked `Account` and `WalletBalance` in one transaction.
- Deleting a `User` cleans up the entire chain.

**Constructor Injection**
All Spring beans use `@RequiredArgsConstructor` (Lombok) for constructor injection. Field injection via `@Autowired` is avoided — constructor injection makes dependencies explicit and simplifies testing.

**Consistent API Response Envelope**
Every endpoint returns `ApiResponse<T>` with `success`, `message`, `data`, and `timestamp`. This gives clients a predictable contract regardless of success or failure.

---

## Sample cURL Commands

**Create a user:**
```bash
curl -X POST http://localhost:9090/api/v1/wallet/users \
  -H "Content-Type: application/json" \
  -d '{"email": "john@example.com"}'
```

**Transfer funds:**
```bash
curl -X POST http://localhost:9090/api/v1/wallet/transfers \
  -H "Content-Type: application/json" \
  -d '{"fromAccount": "1234567890", "toAccount": "0987654321", "amount": 200.00}'
```

**Check balance:**
```bash
curl http://localhost:9090/api/v1/wallet/accounts/1234567890/balance
```

**View transaction history (paginated):**
```bash
# First page, 10 records
curl "http://localhost:9090/api/v1/wallet/accounts/1234567890/transactions?page=0&size=10"

# Second page
curl "http://localhost:9090/api/v1/wallet/accounts/1234567890/transactions?page=1&size=10"
```
