$inputDir = "D:\workspaces\use\use-dtdl\src\org\tzi\use\dtdl\actions"
$outputFile = "D:\workspaces\use\use-dtdl\src\org\tzi\use\dtdl\actions\output.txt"

Get-ChildItem $inputDir -File -Recurse |
ForEach-Object {
    Get-Content $_.FullName
} | Set-Content $outputFile