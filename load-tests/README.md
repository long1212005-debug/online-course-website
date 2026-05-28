# API load test guide

This folder contains a k6 script for the assignment requirement:
test API accessibility and large-volume request handling.

## Tested APIs

- `POST /api/v1/auth/register/student`
- `POST /api/v1/auth/login`
- `GET /api/v1/courses`
- `GET /api/v1/banners/active`
- `GET /api/v1/users/profile`
- `GET /api/v1/payments/create_payment`
- Optional: `POST /api/v1/payments/test/success`

## Prerequisites

Start the whole system first:

```powershell
cd D:\KTPM\online-course-website
docker compose up -d
docker compose ps
```

The API gateway must be available at:

```text
http://localhost:8080
```

Install k6 if it is not installed:

```powershell
winget install k6.k6
```

Open a new terminal after installing k6.

## Run a normal load test

```powershell
cd D:\KTPM\online-course-website
k6 run .\load-tests\k6-api-load-test.js
```

Default scenario:

- ramp to 20 virtual users in 30 seconds
- hold 20 users for 1 minute
- ramp to 50 users in 30 seconds
- hold 50 users for 1 minute
- ramp down to 0 users

## Run with custom settings

```powershell
$env:BASE_URL="http://localhost:8080"
$env:COURSE_ID="1"
$env:TEACHER_ID="1"
$env:TEACHER_EMAIL="teacher@example.com"
k6 run .\load-tests\k6-api-load-test.js
```

## Optional write-heavy payment simulation

Only enable this if you want to test the payment success simulation endpoint.
It writes transaction/enrollment data to the database.

```powershell
$env:RUN_PAYMENT_WRITE_TEST="true"
k6 run .\load-tests\k6-api-load-test.js
```

## What to show in the recorded video

1. Show the system is running:

```powershell
docker compose ps
```

2. Show API gateway is reachable:

```powershell
Invoke-WebRequest http://localhost:8080/api/v1/courses -UseBasicParsing
```

3. Run the load test:

```powershell
k6 run .\load-tests\k6-api-load-test.js
```

4. Explain the final k6 summary:

- `http_reqs`: total API requests
- `http_req_failed`: failed request rate
- `http_req_duration`: response time
- `p(95)`: 95% of requests are faster than this value
- `iterations`: number of completed test flows
- `vus_max`: maximum concurrent virtual users

Pass criteria in this script:

- failed request rate below 5%
- 95th percentile response time below 1500 ms

