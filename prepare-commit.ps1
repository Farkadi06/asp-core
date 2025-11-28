# PowerShell script to prepare commit for asp-core bank detection system
# Run this script from the asp-core directory

Write-Host "Preparing commit for asp-core: Bank Detection System Implementation..." -ForegroundColor Green
Write-Host ""

# Check if git is initialized
if (-not (Test-Path .git)) {
    Write-Host "Git repository already initialized in asp-core." -ForegroundColor Yellow
}

# Stage all changes
Write-Host "Staging changes..." -ForegroundColor Yellow

# Stage modified files
git add src/main/java/com/asp/common/Constants.java
git add src/main/java/com/asp/parsing/detection/DefaultBankDetector.java
git add src/main/java/com/asp/parsing/processor/DefaultIngestionProcessorService.java
git add src/main/java/com/asp/parsing/parser/ParserRegistry.java

# Stage new files
git add src/main/java/com/asp/common/
git add src/main/java/com/asp/service/processing/

# Stage test files
git add src/test/java/com/asp/parsing/detection/DefaultBankDetectorTest.java
git add src/test/java/com/asp/parsing/processor/DefaultIngestionProcessorServiceTest.java

# Stage other modified files (if any)
git add src/main/java/com/asp/parsing/error/ParserErrorLogger.java
git add src/main/java/com/asp/parsing/writer/DefaultTransactionWriter.java
git add src/main/java/com/asp/service/IngestionService.java
git add src/main/java/com/asp/parsing/extraction/S3PdfTextExtractor.java
git add src/main/java/com/asp/controller/internal/IngestionProcessingController.java
git add src/main/java/com/asp/controller/internal/IngestionMetadataController.java

# Stage deleted files
git add src/main/java/com/asp/parsing/extraction/LocalPdfTextExtractor.java

Write-Host "Files staged successfully." -ForegroundColor Green
Write-Host ""

# Show status
Write-Host "Staged files:" -ForegroundColor Cyan
git status --short

Write-Host ""
Write-Host "Ready to commit!" -ForegroundColor Green
Write-Host ""
Write-Host "To commit, run:" -ForegroundColor Yellow
Write-Host '  git commit -F COMMIT_MESSAGE.txt' -ForegroundColor White
Write-Host ""

