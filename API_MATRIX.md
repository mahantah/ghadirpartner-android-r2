# API Matrix Android R5.4

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
`POST /api/orders/add` carries `delivery_address_id` and `settlement_details` when supplied.
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
