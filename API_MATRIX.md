# Proposed R5.12 order protocol

Requires the separately reviewed server package in `mahantah/ghadirpartner#93` before distribution.

- `POST /api/orders/add`: mandatory `invoice_type`; cash/check/credit methods for all purchase groups; explicit boolean credit consent and matching `commitment_sha256` revision in `settlement_details`.
- `items` in the request may contain different price options of one model; the server reprices each authorized option and enforces cumulative model stock.
- Response `purchase_items` contains exact financial rows; `items` stays unique per physical model for warehouse compatibility.
- `approval_status` and `rejection_reason` carry the company decision, separately from financial approval and fulfillment `status`.
- Automation admin/sales: `POST /api/customer-order/approve` and `POST /api/customer-order/reject` (mandatory reason); the server enforces roles and pending status.
- Customer details explicitly refresh the account-owned order list to display the latest company decision.

The historical matrix below describes the selected source baseline, not a claim that the new server package is deployed.

# API Matrix Android R5.6

Portal base: `https://ghadirpartner.ir/partners`.
Automation base: `https://ghadirpartner.ir`.
Both use their existing session cookies. The native app does not replace server authorization.

Portal:
- POST /api/login
- GET /api/me
- POST /api/password-reset/request
- POST /api/password-reset/confirm
- GET /api/orders
- POST /api/orders/add
- POST /api/customer-order/cancel-request
- GET /api/catalog
- GET /api/reports/customer-summary
- POST /api/profile/update
- GET /api/offers
- GET /api/notifications
- POST /api/notifications/read
- GET /api/addresses
- POST /api/addresses (create/update, or `delete_id`)
- GET /api/purchased-devices
- POST /api/native/login-otp/request
- POST /api/native/login-otp/confirm
- GET /api/native/credit
- GET /api/native/proof?token=... (authenticated receipt access)
- Serial PDF is generated locally from GET /api/orders data.
- Official proforma PDFs are generated locally and shared through the app's FileProvider.

Automation:
- POST /api/login
- GET /api/me
- GET /api/orders
- GET /api/customers
- GET /api/inventory
- GET /api/reports/sales
- POST /api/payment
- POST /api/status
- POST /api/staff-password-reset/request
- POST /api/staff-password-reset/confirm

Backend compatibility: customer `requested_payment_method` values are `cash`, `check`, `credit`.
`POST /api/orders/add` carries `address_id` and `settlement_details` when supplied.
Settlement evidence requires finance review; submission does not confirm payment or authorize shipping.

Server compatibility code is maintained separately in `mahantah/ghadirpartner`, including
the R5 native compatibility and R5.4 OTP/settlement patches. Its default branch alone
does not contain all deployed native patches. Do not deploy the default branch as a replacement
for the current production portal.

Recorded server checks/deployments (2026-10-04):
- [R5.4 compatibility deployment](https://github.com/mahantah/ghadirpartner/actions/runs/37199619577)
- [OTP authentication tests](https://github.com/mahantah/ghadirpartner/actions/runs/37200950045)
- [Finance receipt review deployment](https://github.com/mahantah/ghadirpartner/actions/runs/37201132482)

These historical successful runs do not prove that a live account's SMS, credit limit,
order submission or finance review works today. Verify those with an authorized test account.
The PHP tests in `validation/` require the separate matching web source and PHP-WASM dependencies;
they are not standalone Android tests.

R5.6 inbox compatibility: accepts modern items objects and legacy arrays. If the legacy notification endpoint is missing or empty, authenticated order statuses and eligible offers populate the message center. Legacy read state stays local and account-scoped; modern read requests are limited to IDs present in the account inbox. No server deployment or system push was added.
