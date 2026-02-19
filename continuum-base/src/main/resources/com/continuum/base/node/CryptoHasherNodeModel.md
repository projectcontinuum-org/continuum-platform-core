# Crypto Hasher

Generates SHA-256 cryptographic hashes of text values, useful for data integrity verification, deduplication, and security workflows.

## Input Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| data | Table | List<Map<String, Any>> | Input table with text column to hash |

## Output Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| data | Table | List<Map<String, Any>> | Input table plus hash column (SHA-256 hex) |

## Properties
- **inputCol** (string, required): Column containing text to hash
- **outputCol** (string, required): Column name for the hash output

## Behavior
For each row:
1. Extracts text from `inputCol` (empty string if null)
2. Converts to UTF-8 bytes
3. Computes SHA-256 hash using `java.security.MessageDigest`
4. Formats as lowercase hexadecimal string (64 characters)
5. Adds hash to new column `outputCol`
6. Preserves original column

**Properties:**
- Algorithm: SHA-256 (256-bit)
- Output: 64-character lowercase hex string
- Encoding: UTF-8
- Deterministic: Same input always produces same hash

## Use Cases
- Data integrity verification
- Deduplication (hash as unique key)
- Password hashing (though SHA-256 alone not recommended for passwords)
- Data anonymization
- Change detection

## Security Note
SHA-256 is cryptographically secure but not suitable for password storage without additional measures (salt, key stretching). Use bcrypt, PBKDF2, or Argon2 for passwords.

## Example

**Input:**
```json
[
  {"id": 1, "text": "hello"},
  {"id": 2, "text": "world"}
]
```

**Properties:**
```json
{
  "inputCol": "text",
  "outputCol": "hash"
}
```

**Output:**
```json
[
  {
    "id": 1,
    "text": "hello",
    "hash": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
  },
  {
    "id": 2,
    "text": "world",
    "hash": "486ea46224d1bb4fb680f34f7c9ad96a8f24ec88be73ea8e5a6c65260e9cb8a7"
  }
]
```

The hash values are deterministic: hashing "hello" always produces the same 64-character hash.
