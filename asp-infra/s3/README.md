# S3 Bucket Policy - Encryption and Security Enforcement

## Overview

This directory contains the S3 bucket policy for `asp-prod-ingestions` that enforces mandatory encryption, HTTPS-only access, and prevents public access.

## Policy File

- **`bucket-policy.json`** - Strict S3 bucket policy with three security rules

## Policy Statements

### 1. DenyUnEncryptedObjectUploads
- **Effect**: Deny
- **Action**: `s3:PutObject`
- **Condition**: Rejects uploads that do NOT have `s3:x-amz-server-side-encryption: AES256`
- **Purpose**: Enforces mandatory SSE-S3 encryption on all object uploads

### 2. DenyInsecureTransport
- **Effect**: Deny
- **Action**: `s3:*` (all S3 actions)
- **Condition**: Rejects requests when `aws:SecureTransport` is `false`
- **Purpose**: Enforces HTTPS-only access to the bucket (no HTTP allowed)

### 3. DenyPublicAccess
- **Effect**: Deny
- **Actions**: `s3:PutObjectAcl`, `s3:PutBucketAcl`
- **Condition**: Rejects ACL changes that would make objects/bucket public
- **Purpose**: Prevents accidental public exposure of sensitive PDF documents

## Application

### ⚠️ IMPORTANT: Manual Application Required

This policy **MUST be applied manually** via one of the following methods:

### Option 1: AWS Console

1. Navigate to **S3** → **asp-prod-ingestions** bucket
2. Go to **Permissions** tab
3. Scroll to **Bucket policy**
4. Click **Edit**
5. Copy the contents of `bucket-policy.json`
6. Paste into the policy editor
7. Click **Save changes**

### Option 2: AWS CLI

```bash
aws s3api put-bucket-policy \
  --bucket asp-prod-ingestions \
  --policy file://asp-infra/s3/bucket-policy.json
```

### Option 3: Terraform

If using Terraform, reference this policy in your S3 bucket resource:

```hcl
resource "aws_s3_bucket_policy" "asp_ingestions" {
  bucket = aws_s3_bucket.asp_ingestions.id

  policy = file("${path.module}/s3/bucket-policy.json")
}
```

## Compatibility

### Application Code

✅ **No code changes required** - The application already enforces encryption:

- `S3UploadService.java` explicitly sets `.serverSideEncryption(ServerSideEncryption.AES256)`
- All uploads include the `x-amz-server-side-encryption: AES256` header
- This policy only adds infrastructure-level enforcement

### Download Service

✅ **No changes needed** - SSE-S3 is automatically decrypted by AWS:

- `S3DownloadService` does not need encryption headers for downloads
- AWS automatically decrypts SSE-S3 encrypted objects on download
- The policy only affects uploads, not downloads

### Worker & Parser

✅ **No impact** - Worker and parser are unaffected:

- Worker does not interact with S3 (architecture change)
- Parser only processes downloaded files (auto-decrypted by AWS)
- Ingestion flow remains unchanged

## Testing

After applying the policy:

1. **Test encrypted upload** (should succeed):
   ```bash
   # Application uploads with encryption header - will succeed
   ```

2. **Test unencrypted upload** (should fail):
   ```bash
   aws s3 cp test.pdf s3://asp-prod-ingestions/test.pdf
   # Should fail with: Access Denied
   ```

3. **Test HTTP access** (should fail):
   ```bash
   # Any HTTP request to bucket should be denied
   ```

## Security Benefits

- ✅ **Mandatory Encryption**: All PDFs are encrypted at rest (SSE-S3)
- ✅ **HTTPS Only**: Prevents man-in-the-middle attacks
- ✅ **No Public Access**: Prevents accidental data exposure
- ✅ **Infrastructure-Level Enforcement**: Policy enforces what code already does

## Bucket Configuration Requirements

Before applying this policy, ensure:

1. ✅ Bucket default encryption is enabled (SSE-S3 AES256)
2. ✅ Block Public Access settings are configured
3. ✅ Application code includes encryption headers (already done)

## Rollback

If you need to remove the policy:

```bash
aws s3api delete-bucket-policy --bucket asp-prod-ingestions
```

Or via AWS Console: **Permissions** → **Bucket policy** → **Delete**

## Related Files

- `asp-core/src/main/java/com/asp/service/storage/S3UploadService.java` - Application-level encryption
- `asp-core/src/main/resources/s3/s3-encryption-policy.json` - Example policy (documentation only)
- `backend/policies/ApsProdS3Policy.json` - IAM policy for application access

## Notes

- This policy is **deny-based** - it blocks non-compliant requests
- The policy works in conjunction with bucket default encryption
- Application must continue to include encryption headers (already implemented)
- Policy does not affect existing encrypted objects in the bucket

