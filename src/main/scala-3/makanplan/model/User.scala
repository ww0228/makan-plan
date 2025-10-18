package makanplan.model

// App user account
final case class User(
  id: Long,            // DB id
  username: String,    // login/display name
  email: String,       // contact email
  passwordHash: String // hashed password (never store plaintext)
)
