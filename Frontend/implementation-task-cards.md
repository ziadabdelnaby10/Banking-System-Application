# Frontend Implementation Task Cards

## Project direction
- Build the web app as the primary browser-based banking interface
- Build JavaFX desktop app as an internal operations client
- Keep both as client applications that call the backend through the API Gateway
- Maintain backend microservices as the source of truth for business rules and security

## Epic 1: Frontend foundation and shared architecture

### Card FE-001: Define frontend architecture baseline
- Priority: High
- Status: Planned
- Owner: Frontend lead
- Description: Confirm the final frontend architecture: React web app + JavaFX desktop app, both API-driven, no frontend logic in backend services.
- Deliverables:
  - final frontend architecture document
  - role split between web and desktop
  - API consumer pattern
- Acceptance criteria:
  - both frontends clearly consume backend APIs
  - no business logic embedded in UI-only modules
  - architecture approved by team

### Card FE-002: Create shared API contract layer
- Priority: High
- Status: Planned
- Owner: Frontend + backend team
- Description: Establish shared API contracts for all service endpoints and DTOs.
- Deliverables:
  - OpenAPI contract baseline
  - typed DTO definitions for customer, account, transfer, approval, reporting, notification APIs
  - standardized error response model
- Acceptance criteria:
  - all frontend modules use the same API contract
  - DTOs match backend schema
  - common error handling defined

### Card FE-003: Create frontend project structure
- Priority: High
- Status: Planned
- Owner: Frontend lead
- Description: Set up the web and desktop project structure with modules and config.
- Deliverables:
  - React app structure
  - JavaFX app structure
  - environment config files
  - base README and setup steps
- Acceptance criteria:
  - both apps can run locally
  - environment variables documented
  - folder structure matches repo conventions

### Card FE-004: Build centralized auth and session layer
- Priority: High
- Status: Planned
- Owner: Frontend lead
- Description: Implement auth provider, token handling, and protected route handling for both frontends.
- Deliverables:
  - login flow
  - JWT storage and refresh handling
  - route guards for protected pages
  - session timeout handling
- Acceptance criteria:
  - invalid token case handled
  - expired session redirect works
  - protected access is enforced

## Epic 2: Web application MVP

### Card FE-005: Build web app shell and layout
- Priority: High
- Status: Planned
- Owner: Frontend lead
- Description: Create base navigation, dashboard shell, layout containers, and reusable UI primitives.
- Deliverables:
  - top navigation
  - sidebar or app shell
  - page container and loading/error states
  - theme and styling baseline
- Acceptance criteria:
  - app loads without errors
  - dashboard shell is reusable
  - consistent theme applied

### Card FE-006: Implement login and MFA UI
- Priority: High
- Status: Planned
- Owner: Frontend lead
- Description: Build login page and MFA challenge workflow for browser users.
- Deliverables:
  - login form
  - MFA entry screen
  - login API integration
  - failed auth states
- Acceptance criteria:
  - user can log in successfully
  - MFA flow works as expected
  - failed login shows clear message

### Card FE-007: Build customer management screens
- Priority: High
- Status: Planned
- Owner: Frontend team
- Description: Add customer search, detail, create, and update screens.
- Deliverables:
  - customer list page
  - customer detail page
  - create/edit customer form
  - KYC status display
- Acceptance criteria:
  - user can search customers
  - customer profile is editable
  - status information is visible

### Card FE-008: Build account management screens
- Priority: High
- Status: Planned
- Owner: Frontend team
- Description: Create account overview, search, and lifecycle management screens.
- Deliverables:
  - account list page
  - account detail page
  - create account flow
  - freeze/unfreeze controls
- Acceptance criteria:
  - user can view account details
  - account actions call backend correctly
  - account status updates render correctly

### Card FE-009: Build transfer and payment UI
- Priority: High
- Status: Planned
- Owner: Frontend team
- Description: Implement transfer/payment screens with validation and status handling.
- Deliverables:
  - transfer form
  - validation rules
  - transaction detail page
  - status and failure messages
- Acceptance criteria:
  - user can initiate transfer request
  - validation errors are shown
  - pending/success states are reflected in UI

### Card FE-010: Build approval inbox and decision flow
- Priority: High
- Status: Planned
- Owner: Frontend team
- Description: Build approval queue and decision UI for maker-checker workflow.
- Deliverables:
  - approval list view
  - task detail page
  - approve/reject actions
  - notes and reason input
- Acceptance criteria:
  - approver can review tasks
  - decision is submitted to backend
  - reason is captured properly

### Card FE-011: Build reporting and dashboard UI
- Priority: High
- Status: Planned
- Owner: Frontend team
- Description: Create dashboard cards, reports, filters, and chart widgets.
- Deliverables:
  - overview dashboard
  - charts for deposits/withdrawals/transfers
  - approval metrics
  - export-ready report page
- Acceptance criteria:
  - dashboard loads with backend data
  - charts render correctly
  - filters update data views

### Card FE-012: Build notification center
- Priority: Medium
- Status: Planned
- Owner: Frontend team
- Description: Add notification inbox, read/unread states, and alert display.
- Deliverables:
  - notification list
  - notification detail panel
  - read/unread behavior
  - alert category display
- Acceptance criteria:
  - user can view notifications
  - unread markers update correctly
  - notification actions are mapped to backend events

### Card FE-013: Add responsive and accessibility polish
- Priority: Medium
- Status: Planned
- Owner: Frontend team
- Description: Improve usability, accessibility, and responsiveness for web app.
- Deliverables:
  - keyboard-accessible forms
  - responsive layout for tablet/mobile
  - ARIA improvements and screen-reader labeling
  - consistent focus states
- Acceptance criteria:
  - web app documents pass basic accessibility checks
  - pages remain usable on smaller screens

## Epic 3: Java desktop application MVP

### Card FE-014: Initialize JavaFX project skeleton
- Priority: High
- Status: Planned
- Owner: Desktop lead
- Description: Create the desktop app project skeleton and configure JavaFX runtime.
- Deliverables:
  - Maven or Gradle project
  - JavaFX app bootstrap
  - base FXML and CSS structure
  - app main window shell
- Acceptance criteria:
  - app launches locally
  - base views load without errors

### Card FE-015: Implement desktop login and session handling
- Priority: High
- Status: Planned
- Owner: Desktop lead
- Description: Add login screen and secure token management for desktop users.
- Deliverables:
  - login form
  - backend auth integration
  - secure token storage
  - logout handling
- Acceptance criteria:
  - login works against backend identity service
  - tokens are stored securely
  - expired session produces proper re-login flow

### Card FE-016: Build customer lookup and profile screens
- Priority: High
- Status: Planned
- Owner: Desktop team
- Description: Add customer search and profile detail screens for desktop staff.
- Deliverables:
  - customer search view
  - detail view
  - profile update form
  - activity timeline
- Acceptance criteria:
  - user can locate a customer quickly
  - profile information displays correctly
  - updates call the backend API

### Card FE-017: Build account operations screen
- Priority: High
- Status: Planned
- Owner: Desktop team
- Description: Create desktop account management and status review UI.
- Deliverables:
  - account list and filters
  - account detail panel
  - status actions
  - account summary view
- Acceptance criteria:
  - desktop user can review account conditions
  - actions invoke backend API
  - state is refreshed after modification

### Card FE-018: Build approval queue in JavaFX
- Priority: High
- Status: Planned
- Owner: Desktop team
- Description: Implement the desktop approval inbox and decision flow.
- Deliverables:
  - pending approval list
  - approval detail page
  - approve/reject actions
  - notes area
- Acceptance criteria:
  - user can review each approval task
  - backend accepts decisions
  - result is visible immediately in UI

### Card FE-019: Build transaction review screen
- Priority: High
- Status: Planned
- Owner: Desktop team
- Description: Let desktop users review transfers and related transaction details.
- Deliverables:
  - transaction search and filters
  - transaction detail view
  - payment/transfer summary panel
- Acceptance criteria:
  - user can find transactions quickly
  - details show backend data accurately
  - failures and statuses are visible

### Card FE-020: Add desktop dashboard and operational widgets
- Priority: Medium
- Status: Planned
- Owner: Desktop team
- Description: Include operational widgets for summary data and daily tasks.
- Deliverables:
  - dashboard cards
  - open task counts
  - key metrics panels
  - service health or summary widgets
- Acceptance criteria:
  - desktop dashboard loads meaningful operational data
  - widgets refresh correctly
  - metrics align with backend states

### Card FE-021: Package and deploy desktop app
- Priority: Medium
- Status: Planned
- Owner: Desktop lead
- Description: Prepare installable JavaFX desktop distribution for internal use.
- Deliverables:
  - packaged app artifact
  - startup scripts or installer
  - release configuration
  - local deployment instructions
- Acceptance criteria:
  - desktop app launches from packaged artifact
  - versioning is documented
  - installation steps are clear

## Epic 4: Integration, QA, and production hardening

### Card FE-022: Run end-to-end frontend validation
- Priority: High
- Status: Planned
- Owner: Whole frontend team
- Description: Validate critical frontend flows across web and desktop apps.
- Deliverables:
  - login flow validation
  - transfer flow validation
  - approval flow validation
  - reporting flow validation
- Acceptance criteria:
  - all critical user journeys work end-to-end
  - same backend contract is used by both frontends
  - no broken flow in staging

### Card FE-023: Add frontend observability and error handling
- Priority: Medium
- Status: Planned
- Owner: Frontend team
- Description: Standardize frontend logging, timeout handling, and error UI states.
- Deliverables:
  - central error boundary / UI notification system
  - connection failure handling
  - API timeout handling
  - logging strategy
- Acceptance criteria:
  - users see clear failure states
  - errors are captured for debugging
  - critical services degrade gracefully

### Card FE-024: Security and access review
- Priority: High
- Status: Planned
- Owner: Security + frontend
- Description: Confirm auth and role rules are correctly enforced in frontend and backend integration.
- Deliverables:
  - access review checklist
  - role-based route checks
  - token protection audit
  - desktop security review
- Acceptance criteria:
  - no unsupported privileged access in UI
  - roles match backend permissions
  - tokens and local data are secure

## Suggested delivery sequence
1. FE-001 to FE-004
2. FE-005 to FE-013
3. FE-014 to FE-021
4. FE-022 to FE-024

## Definition of done for frontend cards
- implementation matches the agreed API contract
- backend validation remains authoritative
- loading, empty, and error states are implemented
- role-based access is respected
- secure token handling is used
- code is reviewed and tested

## Recommended execution order for learning
1. Set up auth and API layer
2. Build web app login and dashboard
3. Build customer and account screens
4. Build transfer and approval flows
5. Build reporting screens
6. Build Java desktop login and operational screens
7. Validate end-to-end flows and harden app security
