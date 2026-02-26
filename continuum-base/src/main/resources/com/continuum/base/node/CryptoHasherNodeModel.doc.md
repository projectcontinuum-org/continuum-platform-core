# Crypto Hasher

Generate SHA-256 cryptographic hashes of text values for data integrity, deduplication, or security workflows. Convert any text into a unique 64-character fingerprint.

---

## What It Does

The Crypto Hasher takes text from a column and generates a SHA-256 hash - a unique digital fingerprint. The same input always produces the same hash, making it perfect for identifying duplicate data, verifying data integrity, or creating unique identifiers.

**Key Points:**
- ✅ Uses SHA-256 algorithm (industry-standard cryptographic hash)
- ✅ Produces 64-character lowercase hexadecimal string
- ✅ Deterministic: same input = same hash, always
- ✅ One-way: hash cannot be reversed to get original text
- ✅ Original column is preserved
- ⚠️ Not suitable for password storage (use bcrypt/Argon2 instead)

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **data** | Input table with text column to hash |

### Output Ports
| Port | Description |
|------|-------------|
| **data** | Original table plus a new column containing SHA-256 hashes |

### Properties
| Property | Type | Required | Description |
|----------|------|----------|-------------|
| **inputCol** | String | Yes | Name of the column containing text to hash |
| **outputCol** | String | Yes | Name for the new column that will contain the hash |

---

## How It Works

1. **Extract text** from the specified input column
2. **Handle null values** (converts to empty string)
3. **Convert to bytes** using UTF-8 encoding
4. **Compute SHA-256 hash** using Java's `MessageDigest`
5. **Format as hex** (64 lowercase hexadecimal characters)
6. **Add to output** as a new column (original preserved)

**Hash Properties:**
- **Length**: Always 64 characters
- **Format**: Lowercase hexadecimal (0-9, a-f)
- **Algorithm**: SHA-256 (256 bits = 32 bytes = 64 hex chars)
- **Collision resistance**: Extremely unlikely two different inputs produce same hash

---

## Example

Let's hash user comments for deduplication.

**Input:**

| id | message |
|----|---------|
| 1 | hello |
| 2 | world |
| 3 | hello |

**Configuration:**
- **inputCol**: `message`
- **outputCol**: `hash`

**Output:**

| id | message | hash |
|----|---------|------|
| 1 | hello | 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824 |
| 2 | world | 486ea46224d1bb4fb680f34f7c9ad96a8f24ec88be73ea8e5a6c65260e9cb8a7 |
| 3 | hello | 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824 |

**Notice:** Records 1 and 3 have identical hashes because they have the same message ("hello"). This makes duplicate detection easy!

---

## Common Use Cases

- **Data deduplication**: Identify duplicate records quickly by comparing hashes
- **Data integrity**: Verify data hasn't been tampered with by comparing hashes
- **Change detection**: Detect when data changes by comparing old vs new hashes
- **Unique identifiers**: Generate IDs based on content rather than sequence
- **Data anonymization**: Replace sensitive data with hashes (one-way transformation)
- **Cache keys**: Use hashes as keys for caching systems
- **Version control**: Track changes in text/documents
- **Audit trails**: Create verifiable records of data states

---

## Understanding SHA-256

**What is SHA-256?**
- SHA = Secure Hash Algorithm
- 256 = produces a 256-bit (32-byte) output
- Industry-standard cryptographic hash function
- Used in blockchain, SSL/TLS, digital signatures

**Properties:**
- **Deterministic**: Same input → same output
- **One-way**: Cannot reverse hash to get original
- **Fast**: Computes quickly even for large inputs
- **Avalanche effect**: Tiny change in input → completely different hash
- **Collision resistant**: Virtually impossible for two inputs to have same hash

**Example of Avalanche Effect:**
- `"hello"` → `2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824`
- `"Hello"` → `185f8db32271fe25f561a6fc938b2e264306ec304eda518007d1764826381969`
- (Just one letter capitalized, completely different hash!)

---

## Security Considerations

### ✅ Good For:
- Data integrity verification
- Duplicate detection
- Content-based identifiers
- Change detection
- Non-sensitive data anonymization

### ⚠️ Not Recommended For:
- **Password storage** (use bcrypt, PBKDF2, or Argon2 instead)
  - SHA-256 is too fast, vulnerable to brute-force attacks
  - Passwords need salting and key stretching
- **Encryption** (hashing is one-way, not reversible)
- **Message authentication** (use HMAC-SHA256 instead)

### 🔒 Best Practices:
- Hash public or non-sensitive data
- For passwords, use dedicated password hashing algorithms
- Combine with salt for password-like use cases
- Use HMAC if you need message authentication

---

## Advanced Example: Duplicate Detection

**Scenario:** Find duplicate product descriptions

**Input:**

| id | desc |
|----|------|
| A123 | Red T-Shirt Size M |
| B456 | Blue Jeans Size 32 |
| C789 | Red T-Shirt Size M |
| D012 | red t-shirt size m |

**After Hashing:**

| id | desc | hash |
|----|------|------|
| A123 | Red T-Shirt Size M | abc123... |
| B456 | Blue Jeans Size 32 | def456... |
| C789 | Red T-Shirt Size M | abc123... |
| D012 | red t-shirt size m | xyz789... |

**Analysis:**
- A123 and C789 have **same hash** → exact duplicates
- D012 has **different hash** → not a duplicate (different casing)
- To catch case-insensitive duplicates, normalize text first, then hash

**Workflow:**
1. Text Normalizer → normalize text
2. Crypto Hasher → hash normalized text
3. Filter/Group by hash → identify duplicates

---

## Performance & Limitations

**Performance:**
- ✅ Very fast (SHA-256 is optimized)
- ✅ Handles large text efficiently
- ✅ Memory efficient (one row at a time)

**Limitations:**
- Cannot reverse the hash (by design)
- Case-sensitive: "Hello" ≠ "hello"
- Whitespace-sensitive: "hello" ≠ " hello"
- Consider normalizing text before hashing for consistency

---

## Hash Format Details

**Output Format:**
- Always 64 characters long
- Lowercase hexadecimal: `0-9` and `a-f`
- Example: `2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824`

**Empty String Hash:**
- Input: `""`
- Hash: `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
- (Yes, even empty string has a hash!)

**Special Characters:**
- All UTF-8 characters are supported
- Emojis, Unicode, symbols all hash correctly
- `"hello 🌍"` produces a valid hash

---

## Tips & Warnings

⚠️ **Case Sensitivity**
- `"Hello"` and `"hello"` produce **different** hashes
- Normalize text first if you want case-insensitive comparison

⚠️ **Whitespace Matters**
- `"hello"` and `" hello"` are **different**
- Trim whitespace before hashing if needed

⚠️ **Null Values**
- Null/missing values are converted to empty string `""`
- All nulls will have the same hash

💡 **Combine with Text Normalizer**
- For better duplicate detection, normalize before hashing
- Workflow: Input → Text Normalizer → Crypto Hasher
- This catches variations like "Hello", "HELLO", " hello "

💡 **Hash as Primary Key**
- Hashes can serve as content-based IDs
- Useful for distributed systems (no central ID generator needed)
- Be aware of tiny collision risk (practically negligible)

💡 **Verify Integrity**
- Store hash of original data
- Later, re-hash and compare to detect changes
- If hashes match, data is unchanged

---

## Technical Details

- **Algorithm**: SHA-256 (SHA-2 family)
- **Implementation**: Java `java.security.MessageDigest`
- **Encoding**: UTF-8 byte encoding
- **Output Format**: Lowercase hexadecimal string
- **Hash Length**: 256 bits = 32 bytes = 64 hex characters
- **Speed**: ~500 MB/s on modern hardware
- **Collision Probability**: ~1 in 2^256 (astronomically unlikely)
