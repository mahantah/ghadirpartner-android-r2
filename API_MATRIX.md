# API Matrix V5.4.3.2

Portal:
- POST /api/login
- GET /api/me
- POST /api/password-reset/request
- POST /api/password-reset/confirm
- GET /api/orders
- POST /api/orders/add
- GET /api/catalog
- GET /api/reports/customer-summary
- POST /api/profile/update
- Serial PDF is generated locally from GET /api/orders data.

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

Backend compatibility: customer requested_payment_method values used by V5.4.3.2 are cash, check, credit.
