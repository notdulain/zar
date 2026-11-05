## ADDED Requirements

### Requirement: Settings offers a password reset email
Settings SHALL show a Password row under "Signed in as" only while a signed-in account has an email address.

#### Scenario: Signed in with an email
- **WHEN** Settings opens for a signed-in account with an email
- **THEN** the Password row is shown below "Signed in as"

#### Scenario: No email or no session
- **WHEN** there is no signed-in account or it has no email
- **THEN** the Password row is not shown and a reset cannot be opened

### Requirement: The reset needs an explicit confirmation
Opening the reset SHALL show the account email and explain that a link is sent by email, and SHALL NOT send anything until the person taps Send reset link. The address SHALL be the signed-in account's email and SHALL NOT be editable.

#### Scenario: Open and cancel
- **WHEN** the person opens the reset and then cancels, closes or goes back
- **THEN** no email is sent and Settings is unchanged

### Requirement: One send per confirmation
The send SHALL show a loading state, disable the send and cancel actions and ignore Back and close while in flight. Repeated taps SHALL send once.

#### Scenario: Double tap
- **WHEN** Send reset link is tapped twice quickly
- **THEN** exactly one reset email is requested

#### Scenario: Dismissed by swipe while sending
- **WHEN** the sheet is dismissed by a swipe or scrim tap during the send and reopened
- **THEN** the in-flight send is shown and no second send is started

### Requirement: Outcome is explained
On success the sheet SHALL tell the person to check their inbox (and spam) for a link and offer Done. On failure it SHALL stay open, show an existing auth error message in plain words and offer Try again. The person SHALL stay signed in and no account data SHALL change.

#### Scenario: Network failure then retry
- **WHEN** the send fails with no connection and the person taps Try again once online
- **THEN** the error is replaced by the success state

### Requirement: Account changes end the reset
If the signed-in account changes or signs out, any open reset SHALL close and any send in flight SHALL be cancelled and its result ignored. A link SHALL NOT be requested for any address other than the signed-in one.

#### Scenario: Switched account
- **WHEN** a different account signs in while a reset is open
- **THEN** the sheet closes and no email is sent to either address by that attempt afterwards

### Requirement: No sensitive logging
The feature SHALL NOT log the email address or any password.

#### Scenario: A send succeeds or fails
- **WHEN** a reset request succeeds or fails
- **THEN** logs and error text contain neither the password nor a raw backend message with the address
